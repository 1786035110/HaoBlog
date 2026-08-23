<template>
  <form class="studio-editor" novalidate @submit.prevent="submit">
    <div class="editor-heading">
      <div>
        <p class="instrument-label">ARTICLE / WORKING COPY</p>
        <h1>编辑文章</h1>
      </div>
      <p class="dirty-indicator" :data-status="status">{{ statusLabel }}</p>
    </div>

    <p v-if="saveError" class="form-error" role="alert">{{ saveError }}</p>
    <p v-if="conflictVersion !== null" class="form-error" role="alert">
      服务器版本已变为 {{ conflictVersion }}。本地副本已保留，保存已暂停；请重新载入后查看差异。
      <button class="inline-action" type="button" @click="reloadForConflict">重新载入</button>
    </p>
    <p v-if="localError" class="local-warning" role="status">灾难副本提示：{{ localError }}</p>
    <p v-if="workflowError" class="form-error" role="alert">{{ workflowError }}</p>
    <p v-if="previewUrl" class="preview-link" role="status">预览链接已生成：<a :href="previewUrl" target="_blank" rel="noreferrer">打开限时预览</a></p>
    <p v-if="Object.keys(errors).length" class="form-error" role="alert">请先校准标记为错误的字段，当前未发送保存请求。</p>
    <div v-if="recoveryReview" class="recovery-banner" role="alert">
      本地副本已载入，仅供查看，保存已暂停。确认采用本地内容后才会以服务器最新版本继续保存。
      <div class="recovery-actions">
        <button type="button" class="instrument-button" @click="adoptLocalCopy">采用本地副本</button>
        <button type="button" class="quiet-button" @click="discardLocalCopy">放弃本地副本</button>
      </div>
    </div>

    <fieldset class="metadata-grid">
      <legend>METADATA / 文章信号</legend>
      <div class="field-line field-wide">
        <label for="article-title">标题 <span aria-hidden="true">*</span></label>
        <input id="article-title" v-model="form.title" name="title" maxlength="240" autocomplete="off" required :aria-invalid="!!errors.title" aria-describedby="article-title-help article-title-error">
        <small id="article-title-help">公开标题，最多 240 个字符。</small>
        <small v-if="errors.title" id="article-title-error" class="field-error">{{ errors.title }}</small>
      </div>
      <div class="field-line">
        <label for="article-slug">Slug</label>
        <input id="article-slug" v-model="form.slug" name="slug" maxlength="160" pattern="[a-z0-9]+(-[a-z0-9]+)*" autocomplete="off" :aria-invalid="!!errors.slug" aria-describedby="article-slug-error">
        <small v-if="errors.slug" id="article-slug-error" class="field-error">{{ errors.slug }}</small>
      </div>
      <div class="field-line">
        <label for="article-category">分类</label>
        <select id="article-category" v-model="form.categoryId" name="categoryId">
          <option value="">未分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
      </div>
      <div class="field-line field-wide">
        <label for="article-excerpt">摘要</label>
        <textarea id="article-excerpt" v-model="form.excerpt" name="excerpt" maxlength="600" rows="3" />
      </div>
      <div class="field-line">
        <label for="article-seo-title">SEO 标题</label>
        <input id="article-seo-title" v-model="form.seoTitle" name="seoTitle" maxlength="240" autocomplete="off">
      </div>
      <div class="field-line">
        <label for="article-scheduled-at">定时发布时间</label>
        <input id="article-scheduled-at" v-model="form.scheduledAt" name="scheduledAt" type="datetime-local" step="1" :aria-invalid="!!errors.scheduledAt" aria-describedby="article-scheduled-error">
        <small v-if="errors.scheduledAt" id="article-scheduled-error" class="field-error">{{ errors.scheduledAt }}</small>
      </div>
      <div class="field-line field-wide">
        <label for="article-seo-description">SEO 描述</label>
        <textarea id="article-seo-description" v-model="form.seoDescription" name="seoDescription" maxlength="600" rows="3" />
      </div>
      <div class="field-line field-wide">
        <span class="field-label">标签</span>
        <div v-if="tags.length" class="tag-list">
          <label v-for="tag in tags" :key="tag.id" class="tag-option">
            <input v-model="form.tagIds" type="checkbox" :value="tag.id">
            <span>{{ tag.name }}</span>
          </label>
        </div>
        <small v-else>暂无可用标签。</small>
      </div>
      <div class="field-line field-wide">
        <label class="switch-line" for="article-comments-enabled">
          <input id="article-comments-enabled" v-model="form.commentsEnabled" type="checkbox" name="commentsEnabled">
          <span>允许公开评论</span>
        </label>
        <small>关闭后仅影响这篇文章；站点全局开关仍可统一暂停评论。</small>
      </div>
      <div class="field-line field-wide">
        <span class="field-label">媒体 / DIRECT OSS UPLOAD</span>
        <div class="media-upload-box">
          <input ref="fileInput" class="sr-only" type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" @change="selectImage">
          <div class="media-upload-line">
            <span>{{ form.coverMediaId ? `已绑定封面 ${form.coverMediaId}` : '尚未选择封面' }}</span>
            <button class="quiet-button" type="button" :disabled="mediaPhase === 'processing' || mediaPhase === 'uploading' || mediaPhase === 'confirming'" @click="fileInput?.click()">选择图片</button>
          </div>
          <small>JPEG / PNG / WebP；原图 ≤20 MB、输出 ≤5 MB、最长边 ≤2560。</small>
          <p v-if="mediaPhase !== 'idle' && mediaPhase !== 'done'" class="media-status" :data-phase="mediaPhase">{{ mediaPhase === 'processing' ? '处理中…' : mediaPhase === 'uploading' ? '直传对象存储…' : mediaPhase === 'confirming' ? '确认对象元数据…' : mediaPhase === 'error' ? mediaError : '' }}</p>
          <p v-if="mediaPhase === 'done' && mediaAsset" class="media-status">已确认 {{ mediaAsset.width }}×{{ mediaAsset.height }} · {{ mediaAsset.sizeBytes }} B</p>
          <div v-if="mediaAsset" class="media-actions">
            <button class="instrument-button" type="button" @click="insertImage">插入正文</button>
            <button class="quiet-button" type="button" @click="setCover">设为封面</button>
          </div>
          <button v-if="mediaPhase === 'error'" class="quiet-button" type="button" @click="retryMedia">重试媒体上传</button>
        </div>
      </div>
    </fieldset>

    <fieldset class="markdown-panel">
      <legend>MARKDOWN / SOURCE SIGNAL</legend>
      <div class="editor-toolbar" role="group" aria-label="Markdown 视图模式">
        <button v-for="item in modes" :key="item.value" type="button" :aria-pressed="mode === item.value" @click="mode = item.value">{{ item.label }}</button>
        <span class="toolbar-spacer" />
        <span class="byte-count">{{ markdownBytes }} B / 1048576 B</span>
      </div>
      <div class="editor-workspace" :data-mode="mode">
        <label v-if="mode !== 'preview'" class="source-pane" for="article-markdown">
          <span class="sr-only">Markdown 源码</span>
          <textarea id="article-markdown" ref="markdownInput" v-model="form.markdown" name="markdown" rows="24" aria-label="Markdown 源码" :aria-invalid="!!errors.markdown" aria-describedby="article-markdown-error" />
          <small v-if="errors.markdown" id="article-markdown-error" class="field-error">{{ errors.markdown }}</small>
        </label>
        <div v-if="mode !== 'source'" class="preview-pane" aria-label="Markdown 预览">
          <SafeMarkdown :markdown="form.markdown || '在左侧输入 Markdown，右侧将显示安全预览。'" />
        </div>
      </div>
    </fieldset>

    <div class="editor-actions">
      <span class="signal-note">{{ localCopyPresent ? '本地灾难副本已保留。' : (dirty ? '内容尚未保存到服务器。' : '当前工作副本已保存。') }}</span>
      <div class="action-cluster">
        <button class="quiet-button" type="button" :disabled="actionBusy || saving || recoveryReview || status === 'conflict'" @click="createPreview">生成预览</button>
        <button class="quiet-button" type="button" :disabled="actionBusy || saving || recoveryReview || status === 'conflict'" @click="schedule">定时发布</button>
        <button class="instrument-button" type="button" :disabled="actionBusy || saving || recoveryReview || status === 'conflict'" @click="publish">发布</button>
        <button v-if="status === 'error'" class="quiet-button" type="button" :disabled="saving" @click="retrySave">重试保存</button>
        <button class="instrument-button" type="submit" :disabled="saving || recoveryReview || status === 'conflict'">{{ saving ? 'SAVING…' : '手动保存' }}</button>
      </div>
    </div>
  </form>

  <dialog ref="recoveryDialog" class="recovery-dialog" aria-labelledby="recovery-title" aria-describedby="recovery-description" @cancel.prevent>
    <template v-if="recovery">
      <p class="instrument-label">LOCAL COPY / RECOVERY</p>
      <h2 id="recovery-title">发现本地灾难副本</h2>
      <p id="recovery-description">本地副本更新时间：{{ recovery.draft.localUpdatedAt }}。请明确选择下一步。</p>
      <dl v-if="showDiff" class="diff-summary">
        <div><dt>变化字段</dt><dd>{{ recovery.diff.fields.length ? recovery.diff.fields.join('、') : '无元数据变化' }}</dd></div>
        <div><dt>Markdown 行数</dt><dd>服务器 {{ recovery.diff.serverMarkdownLines }} → 本地 {{ recovery.diff.localMarkdownLines }}</dd></div>
        <div><dt>Markdown 字节</dt><dd>服务器 {{ recovery.diff.serverMarkdownBytes }} → 本地 {{ recovery.diff.localMarkdownBytes }}</dd></div>
      </dl>
      <p v-if="recovery.review" class="form-error">该副本基于旧服务器版本，只能先恢复查看；不会自动覆盖服务器内容。</p>
      <div class="dialog-actions">
        <button class="instrument-button" type="button" @click="restoreLocalCopy">恢复本地副本</button>
        <button class="quiet-button" type="button" @click="discardLocalCopy">放弃本地副本</button>
        <button class="quiet-button" type="button" @click="showDiff = !showDiff">{{ showDiff ? '隐藏差异摘要' : '查看差异摘要' }}</button>
      </div>
    </template>
  </dialog>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import SafeMarkdown from '../articles/SafeMarkdown.vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { createArticleAutosave } from '../../composables/useArticleAutosave'
import type { ArticleDraftStore } from '../../utils/articleDraftStore'
import { shouldConfirmArticleLeave, validateArticleForm, type ArticleFormModel } from '../../utils/studioArticleForm'
import { useAdminMedia } from '../../composables/useAdminMedia'

type Article = components['schemas']['AdminArticleResponse']
type Category = components['schemas']['CategoryResponse']
type Tag = components['schemas']['TagResponse']

const props = defineProps<{
  article: Article
  categories: Category[]
  tags: Tag[]
  saveArticle: (form: ArticleFormModel) => Promise<Article>
  publishArticle: (article: Article) => Promise<Article>
  scheduleArticle: (article: Article) => Promise<Article>
  createPreview: (article: Article) => Promise<string>
  draftStore?: ArticleDraftStore
}>()
const session = useAdminSession()
const media = useAdminMedia()
const { phase: mediaPhase, asset: mediaAsset, error: mediaError } = media
const autosave = createArticleAutosave({ article: props.article, saveArticle: props.saveArticle, store: props.draftStore })
const { form, dirty, status, saving, saveError, localError, localCopyPresent, conflictVersion, recovery, recoveryReview, serverArticle, saveNow, start, stop, restoreLocalCopy: restore, discardLocalCopy: discard, adoptLocalCopy: adopt, acceptServerArticle } = autosave

const errors = ref<Record<string, string>>({})
const mode = ref<'source' | 'split' | 'preview'>('split')
const recoveryDialog = ref<HTMLDialogElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const markdownInput = ref<HTMLTextAreaElement | null>(null)
const showDiff = ref(false)
const actionBusy = ref(false)
const workflowError = ref('')
const previewUrl = ref('')
const modes = [
  { value: 'source' as const, label: '源码' },
  { value: 'split' as const, label: '分栏' },
  { value: 'preview' as const, label: '预览' },
]
const markdownBytes = computed(() => new TextEncoder().encode(form.markdown).length)
const statusLabel = computed(() => ({ synced: 'SYNCED', unsaved: 'UNSAVED', saving: 'SAVING…', error: 'SAVE ERROR', conflict: 'CONFLICT', recovery: 'RECOVERY' }[status.value]))

function submit() {
  errors.value = validateArticleForm(form)
  if (Object.keys(errors.value).length) return
  void saveNow(true)
}

function retrySave() { void saveNow(true) }

async function ensureSaved() {
  errors.value = validateArticleForm(form)
  if (Object.keys(errors.value).length) return false
  if (dirty.value && !(await saveNow(true))) return false
  return true
}

async function runWorkflow(action: (article: Article) => Promise<Article>) {
  if (actionBusy.value || !(await ensureSaved())) return
  actionBusy.value = true
  workflowError.value = ''
  try {
    await acceptServerArticle(await action(serverArticle.value))
  } catch (cause) {
    workflowError.value = cause instanceof Error ? cause.message : '文章工作流操作失败。'
  } finally {
    actionBusy.value = false
  }
}

function publish() { void runWorkflow(props.publishArticle) }
function schedule() { void runWorkflow(props.scheduleArticle) }
async function createPreview() {
  if (actionBusy.value || !(await ensureSaved())) return
  actionBusy.value = true
  workflowError.value = ''
  try {
    previewUrl.value = await props.createPreview(serverArticle.value)
  } catch (cause) {
    workflowError.value = cause instanceof Error ? cause.message : '预览链接生成失败。'
  } finally {
    actionBusy.value = false
  }
}

function selectImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) void media.upload(file)
  ;(event.target as HTMLInputElement).value = ''
}

function setCover() {
  if (mediaAsset.value) form.coverMediaId = mediaAsset.value.id
}

async function insertImage() {
  if (!mediaAsset.value) return
  const input = markdownInput.value
  const start = input?.selectionStart ?? form.markdown.length
  const end = input?.selectionEnd ?? start
  const name = '图片'
  const markdown = `![${name}](${mediaAsset.value.publicUrl})`
  form.markdown = `${form.markdown.slice(0, start)}${markdown}${form.markdown.slice(end)}`
  await nextTick()
  input?.focus()
  input?.setSelectionRange(start + markdown.length, start + markdown.length)
}

function retryMedia() { void media.retry() }

function restoreLocalCopy() {
  restore()
  showDiff.value = false
  recoveryDialog.value?.close()
}

function discardLocalCopy() {
  void discard()
  showDiff.value = false
  recoveryDialog.value?.close()
}

function adoptLocalCopy() { void adopt() }
function reloadForConflict() { window.location.reload() }

function leaveGuard() {
  return shouldConfirmArticleLeave(dirty.value, !!session.session.value, () => window.confirm('当前文章有未保存修改，确定离开吗？'))
}

onBeforeRouteLeave(() => leaveGuard())

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value || !session.session.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(recovery, value => {
  if (value && recoveryDialog.value && !recoveryDialog.value.open) recoveryDialog.value.showModal()
  if (!value && recoveryDialog.value?.open) recoveryDialog.value.close()
})

onMounted(() => {
  start()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  stop()
  window.removeEventListener('beforeunload', beforeUnload)
})

defineExpose({ form, dirty, status, saveNow })
</script>

<style scoped>
.studio-editor { max-width: 82rem; margin: 0 auto; }
.editor-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-8); }
.editor-heading h1 { margin: .8rem 0 0; font-size: clamp(2.2rem, 7vw, 5rem); }
.dirty-indicator { margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); letter-spacing: .12em; }
.dirty-indicator[data-status='unsaved'], .dirty-indicator[data-status='error'], .dirty-indicator[data-status='conflict'] { color: var(--color-warn); }
.dirty-indicator[data-status='recovery'] { color: var(--color-accent); }
.metadata-grid, .markdown-panel { min-width: 0; margin: 0 0 var(--space-8); padding: var(--space-4); border: 1px solid var(--color-border); }
legend { padding: 0 var(--space-2); color: var(--color-accent); font: var(--text-xs)/1 var(--font-mono); letter-spacing: .12em; }
.metadata-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-6) var(--space-8); }
.field-wide { grid-column: 1 / -1; }
.field-line { display: grid; gap: var(--space-2); min-width: 0; }
.field-line label, .field-label { color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); letter-spacing: .08em; }
.switch-line { display: inline-flex; align-items: center; gap: .6rem; width: fit-content; cursor: pointer; }
.switch-line input { width: 1rem; height: 1rem; accent-color: var(--color-accent); }
.field-line input, .field-line textarea, .field-line select { width: 100%; min-width: 0; padding: .7rem 0; border: 0; border-bottom: 1px solid var(--color-border); border-radius: 0; outline: 0; background: transparent; color: var(--color-text-main); font: var(--text-base)/1.45 var(--font-body); }
.field-line textarea { resize: vertical; }
.field-line select { appearance: auto; }
.field-line input:focus, .field-line textarea:focus, .field-line select:focus { border-bottom-color: var(--color-accent); }
.field-line small, .cover-placeholder small { color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.field-error, .form-error { color: var(--color-warn) !important; }
.local-warning { color: var(--color-accent); font: var(--text-xs)/1.5 var(--font-mono); }
.preview-link { color: var(--color-accent); font: var(--text-sm)/1.5 var(--font-mono); }
.preview-link a { color: inherit; }
.tag-list { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-4); }
.tag-option { display: inline-flex; align-items: center; gap: .4rem; color: var(--color-text-main); font: var(--text-sm)/1.4 var(--font-body); cursor: pointer; }
.cover-placeholder { display: grid; gap: .35rem; padding: var(--space-3); border: 1px dashed var(--color-border); color: var(--color-text-muted); font: var(--text-sm)/1.4 var(--font-mono); }
.media-upload-box { display: grid; gap: var(--space-3); padding: var(--space-3); border: 1px dashed var(--color-border); color: var(--color-text-muted); font: var(--text-sm)/1.4 var(--font-mono); }
.media-upload-line, .media-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-3); }
.media-status { margin: 0; color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); }
.media-status[data-phase='error'] { color: var(--color-warn); }
.editor-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin-bottom: var(--space-3); }
.editor-toolbar button { padding: .45rem .7rem; border: 1px solid var(--color-border); background: transparent; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); cursor: pointer; }
.editor-toolbar button[aria-pressed='true'], .editor-toolbar button:focus-visible, .editor-toolbar button:hover { border-color: var(--color-accent); color: var(--color-accent); }
.toolbar-spacer { flex: 1; }
.byte-count { color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
.editor-workspace { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); min-height: 30rem; border: 1px solid var(--color-border); }
.editor-workspace[data-mode='source'], .editor-workspace[data-mode='preview'] { grid-template-columns: minmax(0, 1fr); }
.source-pane, .preview-pane { min-width: 0; padding: var(--space-3); }
.source-pane { display: grid; grid-template-rows: minmax(0, 1fr) auto; border-right: 1px solid var(--color-border); }
.source-pane textarea { width: 100%; height: 100%; min-height: 28rem; padding: var(--space-3); border: 0; resize: vertical; outline: 0; background: var(--color-code-bg); color: var(--color-text-main); font: var(--text-sm)/1.6 var(--font-mono); }
.preview-pane { overflow: auto; background: color-mix(in srgb, var(--color-bg-sub) 55%, transparent); }
.editor-actions, .recovery-actions, .action-cluster, .dialog-actions { display: flex; align-items: center; gap: var(--space-3); }
.editor-actions { justify-content: space-between; }
.signal-note { margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
.instrument-button, .quiet-button, .inline-action { width: fit-content; padding: .7rem 1rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); letter-spacing: .08em; }
.quiet-button, .inline-action { border-color: var(--color-border); color: var(--color-text-muted); }
.inline-action { padding: .2rem .4rem; margin-left: .35rem; }
.instrument-button:hover:not(:disabled), .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-bg-base); }
.quiet-button:hover:not(:disabled), .quiet-button:focus-visible, .inline-action:hover, .inline-action:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.instrument-button:disabled, .quiet-button:disabled { cursor: wait; opacity: .55; }
.recovery-banner { display: grid; gap: var(--space-3); margin: 0 0 var(--space-6); padding: var(--space-4); border: 1px solid var(--color-accent); color: var(--color-text-main); font: var(--text-sm)/1.5 var(--font-mono); }
.recovery-dialog { width: min(36rem, calc(100vw - 2rem)); padding: var(--space-6); border: 1px solid var(--color-accent); background: var(--color-bg-base); color: var(--color-text-main); }
.recovery-dialog::backdrop { background: color-mix(in srgb, var(--color-bg-base) 78%, transparent); }
.recovery-dialog h2 { margin: .75rem 0; font-size: var(--text-xl); }
.recovery-dialog p { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.diff-summary { display: grid; gap: var(--space-3); margin: var(--space-4) 0; padding: var(--space-3); border-left: 2px solid var(--color-accent); font: var(--text-xs)/1.5 var(--font-mono); }
.diff-summary div { display: grid; grid-template-columns: 9rem 1fr; gap: var(--space-3); }
.diff-summary dt { color: var(--color-accent); }
.diff-summary dd { margin: 0; color: var(--color-text-muted); }
.dialog-actions { flex-wrap: wrap; justify-content: end; margin-top: var(--space-6); }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 720px) { .metadata-grid { grid-template-columns: minmax(0, 1fr); } .field-wide { grid-column: auto; } .editor-workspace { grid-template-columns: minmax(0, 1fr); } .source-pane { border-right: 0; border-bottom: 1px solid var(--color-border); } .editor-workspace[data-mode='split'] .source-pane { min-height: 22rem; } }
@media (max-width: 360px) { .metadata-grid, .markdown-panel { padding: var(--space-3); } .editor-heading { align-items: start; flex-direction: column; } .editor-actions, .action-cluster { align-items: start; flex-direction: column; } .editor-toolbar { gap: .35rem; } .toolbar-spacer { display: none; } .dialog-actions, .recovery-actions { align-items: stretch; flex-direction: column; } .diff-summary div { grid-template-columns: 1fr; gap: .2rem; } }
</style>
