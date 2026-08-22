import type { components } from '@haoblog/api-client'
import { computed, reactive, ref, toRaw, watch, type Ref } from 'vue'
import { AdminSessionError } from './useAdminSession'
import { articleFormSnapshot, articleToForm, validateArticleForm, type ArticleFormModel } from '../utils/studioArticleForm'
import { nativeArticleDraftStore, type ArticleDraftRecord, type ArticleDraftStore } from '../utils/articleDraftStore'

type Article = components['schemas']['AdminArticleResponse']
type SaveArticle = (form: ArticleFormModel) => Promise<Article>
type AutosaveStatus = 'synced' | 'unsaved' | 'saving' | 'error' | 'conflict' | 'recovery'

export type ArticleDiffSummary = {
  fields: string[]
  serverMarkdownLines: number
  localMarkdownLines: number
  serverMarkdownBytes: number
  localMarkdownBytes: number
}

type Options = {
  article: Article
  saveArticle: SaveArticle
  store?: ArticleDraftStore
  intervalMs?: number
  now?: () => Date
}

const fieldLabels: Record<keyof ArticleFormModel, string> = {
  title: '标题', slug: 'Slug', excerpt: '摘要', seoTitle: 'SEO 标题', seoDescription: 'SEO 描述',
  categoryId: '分类', tagIds: '标签', coverMediaId: '封面', scheduledAt: '定时发布时间', markdown: 'Markdown', version: '版本',
  commentsEnabled: '评论开关',
}

function lineCount(value: string) { return value ? value.split(/\r?\n/).length : 0 }
function cloneForm(form: ArticleFormModel) {
  return structuredClone({ ...toRaw(form), tagIds: [...form.tagIds] })
}

export function articleDiffSummary(server: ArticleFormModel, local: ArticleFormModel): ArticleDiffSummary {
  const fields = (Object.keys(fieldLabels) as (keyof ArticleFormModel)[])
    .filter(field => field !== 'version' && articleFormSnapshot({ ...server, [field]: local[field] } as ArticleFormModel) !== articleFormSnapshot(server))
    .map(field => fieldLabels[field])
  return {
    fields,
    serverMarkdownLines: lineCount(server.markdown),
    localMarkdownLines: lineCount(local.markdown),
    serverMarkdownBytes: new TextEncoder().encode(server.markdown).length,
    localMarkdownBytes: new TextEncoder().encode(local.markdown).length,
  }
}

export function createArticleAutosave(options: Options) {
  const store = options.store || nativeArticleDraftStore
  const intervalMs = options.intervalMs ?? 15_000
  const now = options.now || (() => new Date())
  const form = reactive(articleToForm(options.article))
  const baseline = ref(articleFormSnapshot(form))
  const serverArticle = ref(options.article)
  const status = ref<AutosaveStatus>('synced')
  const saving = ref(false)
  const saveError = ref('')
  const localError = ref('')
  const localCopyPresent = ref(false)
  const conflictVersion = ref<number | null>(null)
  const recovery = ref<{ draft: ArticleDraftRecord; diff: ArticleDiffSummary; review: boolean } | null>(null)
  const recoveryReview = ref(false)
  const paused = ref(false)
  const dirty = computed(() => articleFormSnapshot(form) !== baseline.value)
  let interval: ReturnType<typeof setInterval> | undefined
  let localWriteTimer: ReturnType<typeof setTimeout> | undefined
  let stopWatch: (() => void) | undefined
  let started = false

  function markUnsaved() {
    if (!saving.value && !recoveryReview.value && status.value !== 'conflict' && status.value !== 'error') status.value = 'unsaved'
  }

  async function persistLocalCopy() {
    if (!dirty.value) return
    try {
      await store.put({
        articleId: options.article.id,
        form: cloneForm(form),
        baseVersion: form.version ?? serverArticle.value.version,
        localUpdatedAt: now().toISOString(),
      })
      localCopyPresent.value = true
      localError.value = ''
    } catch (cause) {
      localError.value = cause instanceof Error ? cause.message : '本地副本保存失败。'
    }
  }

  function queueLocalCopy() {
    markUnsaved()
    if (localWriteTimer) clearTimeout(localWriteTimer)
    localWriteTimer = setTimeout(() => { void persistLocalCopy() }, 300)
  }

  async function discoverLocalCopy() {
    try {
      const draft = await store.get(options.article.id)
      if (!draft) return
      const serverForm = articleToForm(serverArticle.value)
      if (articleFormSnapshot(draft.form) === articleFormSnapshot(serverForm) && draft.baseVersion === serverArticle.value.version) {
        await store.delete(options.article.id)
        return
      }
      localCopyPresent.value = true
      recovery.value = {
        draft,
        diff: articleDiffSummary(serverForm, draft.form),
        review: draft.baseVersion !== serverArticle.value.version,
      }
    } catch (cause) {
      localError.value = cause instanceof Error ? cause.message : '本地副本读取失败。'
    }
  }

  async function discardLocalCopy() {
    try {
      await store.delete(options.article.id)
      localCopyPresent.value = false
      localError.value = ''
    } catch (cause) {
      localError.value = cause instanceof Error ? cause.message : '本地副本删除失败。'
    }
    Object.assign(form, articleToForm(serverArticle.value))
    baseline.value = articleFormSnapshot(form)
    recovery.value = null
    recoveryReview.value = false
    paused.value = false
    conflictVersion.value = null
    saveError.value = ''
    status.value = 'synced'
  }

  function restoreLocalCopy() {
    if (!recovery.value) return
    Object.assign(form, cloneForm(recovery.value.draft.form))
    const review = recovery.value.review
    recovery.value = null
    recoveryReview.value = review
    paused.value = review
    status.value = review ? 'recovery' : 'unsaved'
  }

  async function adoptLocalCopy() {
    recoveryReview.value = false
    paused.value = false
    conflictVersion.value = null
    saveError.value = ''
    form.version = serverArticle.value.version
    status.value = 'unsaved'
    await persistLocalCopy()
  }

  async function acceptServerArticle(saved: Article) {
    serverArticle.value = saved
    Object.assign(form, articleToForm(saved))
    baseline.value = articleFormSnapshot(form)
    conflictVersion.value = null
    recovery.value = null
    recoveryReview.value = false
    paused.value = false
    saveError.value = ''
    try {
      await store.delete(options.article.id)
      localCopyPresent.value = false
      localError.value = ''
    } catch (cause) {
      localCopyPresent.value = true
      localError.value = cause instanceof Error ? cause.message : '服务器已保存，但本地副本清理失败。'
    }
    status.value = 'synced'
  }

  async function saveNow(manual = false) {
    if (saving.value || recoveryReview.value || status.value === 'conflict') return false
    if (!dirty.value) return true
    const errors = validateArticleForm(form)
    if (Object.keys(errors).length) return false
    if (manual) paused.value = false
    const requested = cloneForm(form)
    const requestedSnapshot = articleFormSnapshot(requested)
    saving.value = true
    status.value = 'saving'
    saveError.value = ''
    try {
      const saved = await options.saveArticle(requested)
      serverArticle.value = saved
      if (articleFormSnapshot(form) === requestedSnapshot) {
        Object.assign(form, articleToForm(saved))
        baseline.value = articleFormSnapshot(form)
        try {
          await store.delete(options.article.id)
          localCopyPresent.value = false
          localError.value = ''
        } catch (cause) {
          localCopyPresent.value = true
          localError.value = cause instanceof Error ? cause.message : '服务器已保存，但本地副本清理失败。'
        }
        status.value = 'synced'
      } else {
        form.version = saved.version
        baseline.value = requestedSnapshot
        await persistLocalCopy()
        status.value = 'unsaved'
      }
      conflictVersion.value = null
      paused.value = false
      return true
    } catch (cause) {
      if (cause instanceof AdminSessionError && cause.status === 409 && cause.problem?.code === 'ARTICLE_VERSION_CONFLICT') {
        conflictVersion.value = cause.problem.currentVersion ?? null
        status.value = 'conflict'
      } else {
        status.value = 'error'
      }
      paused.value = true
      saveError.value = cause instanceof Error ? cause.message : '文章保存失败。'
      await persistLocalCopy()
      return false
    } finally {
      saving.value = false
    }
  }

  function tick() {
    if (!paused.value && !saving.value && dirty.value) void saveNow()
  }

  function start() {
    if (started) return
    started = true
    stopWatch = watch(form, queueLocalCopy, { deep: true })
    interval = setInterval(tick, intervalMs)
    void discoverLocalCopy()
  }

  function stop() {
    if (interval) clearInterval(interval)
    if (localWriteTimer) clearTimeout(localWriteTimer)
    stopWatch?.()
    interval = undefined
    localWriteTimer = undefined
    started = false
  }

  return {
    form,
    baseline,
    dirty,
    status,
    saving,
    saveError,
    localError,
    localCopyPresent,
    conflictVersion,
    recovery,
    recoveryReview,
    serverArticle: serverArticle as Ref<Article>,
    saveNow,
    tick,
    start,
    stop,
    persistLocalCopy,
    restoreLocalCopy,
    discardLocalCopy,
    adoptLocalCopy,
    acceptServerArticle,
  }
}

export type ArticleAutosave = ReturnType<typeof createArticleAutosave>
