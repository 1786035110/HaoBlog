<template>
  <section class="versions-page" aria-labelledby="versions-title">
    <div class="page-heading">
      <div>
        <p class="instrument-label">ARTICLE / VERSION LOG</p>
        <h1 id="versions-title">历史版本</h1>
        <p v-if="article" class="page-caption">{{ article.title }} · WORKING COPY V{{ article.version }}</p>
      </div>
      <div class="heading-actions">
        <NuxtLink class="quiet-button" :to="`/studio/articles/${articleId}`">返回编辑</NuxtLink>
        <NuxtLink class="quiet-button" to="/studio/articles">文章日志</NuxtLink>
      </div>
    </div>

    <p v-if="loadError" class="form-error" role="alert">{{ loadError }}</p>
    <p v-if="restoreMessage" class="success-note" role="status">{{ restoreMessage }}</p>
    <p v-if="restoreError" class="form-error" role="alert">{{ restoreError }}</p>

    <div v-if="article" class="version-console">
      <div class="console-head">
        <span>SELECT TWO SIGNALS</span>
        <span>PAGE {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / pageSize)) }}</span>
      </div>
      <p class="console-help">先分别指定基线和目标，再计算 Markdown 行级差异。列表不会批量传输正文。</p>
      <p v-if="loading" class="signal-note" role="status">正在读取版本信号…</p>
      <p v-else-if="!items.length" class="empty-signal">当前文章还没有历史版本。</p>
      <ol v-else class="version-log">
        <li v-for="item in items" :key="item.id" class="version-entry">
          <div class="version-marker" :data-current="item.currentPublished" aria-hidden="true" />
          <div class="version-main">
            <div class="version-title-line">
              <strong>V{{ item.sourceArticleVersion }}</strong>
              <span v-if="item.currentPublished" class="public-marker">CURRENT PUBLIC</span>
              <time :datetime="item.createdAt">{{ formatDate(item.createdAt) }}</time>
            </div>
            <p class="version-reason">{{ item.changeReason || '未记录原因' }}</p>
            <div class="version-actions">
              <label class="select-signal"><input v-model="baselineId" type="radio" name="baseline-version" :value="item.id"> 基线</label>
              <label class="select-signal"><input v-model="targetId" type="radio" name="target-version" :value="item.id"> 目标</label>
              <button class="quiet-button" type="button" @click="requestRestore(item)">恢复到工作副本</button>
            </div>
          </div>
        </li>
      </ol>

      <div v-if="total > pageSize" class="pagination" aria-label="版本分页">
        <button class="plain-button" type="button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
        <span>{{ total }} VERSIONS</span>
        <button class="plain-button" type="button" :disabled="(page + 1) * pageSize >= total || loading" @click="changePage(page + 1)">下一页</button>
      </div>
    </div>

    <section v-if="selectedBaseline && selectedTarget" class="compare-console" aria-labelledby="compare-title">
      <div class="compare-selection" id="compare-title">
        <span>基线 / V{{ selectedBaseline.sourceArticleVersion }}</span>
        <span aria-hidden="true">→</span>
        <span>目标 / V{{ selectedTarget.sourceArticleVersion }}</span>
        <button class="instrument-button" type="button" :disabled="detailsLoading || baselineId === targetId" @click="compareVersions">
          {{ detailsLoading ? 'READING…' : '比较两个版本' }}
        </button>
      </div>
      <p v-if="baselineId === targetId" class="form-error" role="alert">基线和目标必须是两个不同版本。</p>
      <div v-if="baselineDetail && targetDetail" class="detail-strip">
        <div><span>基线标题</span><strong>{{ baselineDetail.title }}</strong></div>
        <div><span>目标标题</span><strong>{{ targetDetail.title }}</strong></div>
      </div>
      <StudioVersionDiff v-if="baselineDetail && targetDetail" :left="baselineDetail" :right="targetDetail" />
      <p v-else class="signal-note">选择两个版本后，差异正文只在比较操作中读取。</p>
    </section>
  </section>

  <dialog ref="restoreDialog" class="restore-dialog" aria-labelledby="restore-title" aria-describedby="restore-description" @cancel.prevent>
    <template v-if="restoreTarget">
      <p class="instrument-label">RESTORE / WORKING COPY</p>
      <h2 id="restore-title">恢复 V{{ restoreTarget.sourceArticleVersion }}？</h2>
      <p id="restore-description">这会覆盖当前工作副本并递增文章版本。历史快照和当前公开版本均保持不变；恢复后请重新预览并发布。</p>
      <div class="dialog-actions">
        <button class="instrument-button" type="button" :disabled="restoring" @click="confirmRestore">{{ restoring ? 'RESTORING…' : '确认恢复' }}</button>
        <button class="quiet-button" type="button" :disabled="restoring" @click="closeRestore">取消</button>
      </div>
    </template>
  </dialog>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { computed, onMounted, reactive, ref } from 'vue'
import { AdminSessionError } from '../../../../composables/useAdminSession'
import StudioVersionDiff from '../../../../components/studio/StudioVersionDiff.vue'

definePageMeta({ layout: 'studio' })

type Article = components['schemas']['AdminArticleResponse']
type VersionSummary = components['schemas']['AdminArticleVersionSummary']
type Version = components['schemas']['AdminArticleVersionResponse']

const route = useRoute()
const articleId = String(route.params.id)
const { getArticle, listArticleVersions, getArticleVersion, restoreArticleVersion } = useAdminContent()
const article = ref<Article | null>(null)
const items = ref<VersionSummary[]>([])
const summaries = reactive(new Map<string, VersionSummary>())
const total = ref(0)
const page = ref(0)
const pageSize = 20
const loading = ref(false)
const detailsLoading = ref(false)
const loadError = ref('')
const restoreError = ref('')
const restoreMessage = ref('')
const baselineId = ref('')
const targetId = ref('')
const baselineDetail = ref<Version | null>(null)
const targetDetail = ref<Version | null>(null)
const restoreTarget = ref<VersionSummary | null>(null)
const restoreDialog = ref<HTMLDialogElement | null>(null)
const restoring = ref(false)

const selectedBaseline = computed(() => summaries.get(baselineId.value))
const selectedTarget = computed(() => summaries.get(targetId.value))

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [current, result] = await Promise.all([
      article.value ? Promise.resolve(article.value) : getArticle(articleId),
      listArticleVersions(articleId, { page: page.value, size: pageSize }),
    ])
    article.value = current
    items.value = result.items
    total.value = result.total
    for (const item of result.items) summaries.set(item.id, item)
    const first = result.items[0]
    if (!baselineId.value && first) baselineId.value = result.items[1]?.id || first.id
    if (!targetId.value && first) targetId.value = first.id
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '版本列表暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function changePage(next: number) {
  page.value = next
  await load()
}

async function compareVersions() {
  if (!baselineId.value || !targetId.value || baselineId.value === targetId.value) return
  detailsLoading.value = true
  restoreError.value = ''
  try {
    ;[baselineDetail.value, targetDetail.value] = await Promise.all([
      getArticleVersion(articleId, baselineId.value),
      getArticleVersion(articleId, targetId.value),
    ])
  } catch (cause) {
    restoreError.value = cause instanceof Error ? cause.message : '版本详情暂时不可用。'
  } finally {
    detailsLoading.value = false
  }
}

function requestRestore(item: VersionSummary) {
  restoreTarget.value = item
  restoreError.value = ''
  restoreMessage.value = ''
  restoreDialog.value?.showModal()
}

function closeRestore() {
  if (!restoring.value) restoreDialog.value?.close()
}

async function confirmRestore() {
  if (!article.value || !restoreTarget.value) return
  restoring.value = true
  restoreError.value = ''
  try {
    article.value = await restoreArticleVersion(articleId, restoreTarget.value.id, article.value.version)
    restoreMessage.value = `V${restoreTarget.value.sourceArticleVersion} 已恢复到工作副本（当前工作版本 ${article.value.version}）。请返回编辑器重新预览和发布。`
    restoreDialog.value?.close()
    await load()
  } catch (cause) {
    if (cause instanceof AdminSessionError && cause.status === 409 && cause.problem?.currentVersion !== undefined) {
      restoreError.value = `恢复冲突：服务器工作版本已变为 ${cause.problem.currentVersion}，请重新加载后再试。`
    } else {
      restoreError.value = cause instanceof Error ? cause.message : '恢复失败，工作副本未确认改变。'
    }
  } finally {
    restoring.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.versions-page { max-width: 82rem; margin: 0 auto; }
.page-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-6); margin-bottom: var(--space-8); }
.page-heading h1 { margin: .75rem 0 0; font-size: clamp(2.4rem, 8vw, 6rem); }
.page-caption, .console-help, .version-reason { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.page-caption { margin: .75rem 0 0; }
.heading-actions, .version-actions, .dialog-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
.quiet-button, .instrument-button { display: inline-block; width: fit-content; padding: .7rem 1rem; border: 1px solid var(--color-border); background: transparent; color: var(--color-text-muted); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); text-decoration: none; }
.instrument-button { border-color: var(--color-accent); color: var(--color-accent); }
.quiet-button:hover, .quiet-button:focus-visible, .instrument-button:hover:not(:disabled), .instrument-button:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.instrument-button:hover:not(:disabled), .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-bg-base); }
.instrument-button:disabled { cursor: wait; opacity: .55; }
.form-error { color: var(--color-warn); font: var(--text-sm)/1.5 var(--font-mono); }
.success-note { padding: var(--space-3); border-left: 2px solid var(--color-accent); color: var(--color-accent); font: var(--text-sm)/1.5 var(--font-mono); }
.version-console, .compare-console { min-width: 0; }
.console-head, .compare-selection { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); letter-spacing: .08em; }
.console-help { margin: var(--space-3) 0 var(--space-6); }
.version-log { position: relative; display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.version-log::before { position: absolute; top: 0; bottom: 0; left: .35rem; width: 1px; background: var(--color-border); content: ''; }
.version-entry { position: relative; display: grid; grid-template-columns: 1rem minmax(0, 1fr); gap: var(--space-4); padding: var(--space-4) 0; }
.version-marker { z-index: 1; width: .75rem; height: .75rem; margin-top: .25rem; border: 1px solid var(--color-border); border-radius: 50%; background: var(--color-bg-base); }
.version-marker[data-current='true'] { border-color: var(--color-accent); background: var(--color-accent); box-shadow: 0 0 0 3px var(--color-bg-base); }
.version-main { min-width: 0; padding-bottom: var(--space-4); border-bottom: 1px solid color-mix(in srgb, var(--color-border) 65%, transparent); }
.version-title-line { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-3); color: var(--color-text-main); font: var(--text-sm)/1.4 var(--font-mono); }
.version-title-line strong { color: var(--color-accent); font-size: var(--text-lg); }
.version-title-line time { color: var(--color-text-muted); }
.public-marker { color: var(--color-accent); font-size: var(--text-xs); letter-spacing: .08em; }
.version-reason { margin: .45rem 0 var(--space-3); }
.select-signal { color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); cursor: pointer; }
.select-signal input { accent-color: var(--color-accent); }
.pagination { display: flex; align-items: center; justify-content: center; gap: var(--space-6); margin: var(--space-6) 0; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
.plain-button { padding: .4rem 0; border: 0; background: transparent; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); cursor: pointer; }
.plain-button:hover:not(:disabled), .plain-button:focus-visible { color: var(--color-accent); }
.plain-button:disabled { cursor: not-allowed; opacity: .5; }
.empty-signal, .signal-note { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.compare-console { margin-top: var(--space-8); padding-top: var(--space-4); border-top: 1px solid var(--color-border); }
.compare-selection { justify-content: start; flex-wrap: wrap; }
.compare-selection .instrument-button { margin-left: auto; }
.detail-strip { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); margin: var(--space-6) 0; }
.detail-strip div { display: grid; gap: .4rem; min-width: 0; padding: var(--space-3); border-left: 2px solid var(--color-border); }
.detail-strip span { color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.detail-strip strong { overflow-wrap: anywhere; color: var(--color-text-main); font: var(--text-sm)/1.4 var(--font-body); }
.restore-dialog { width: min(34rem, calc(100vw - 2rem)); padding: var(--space-6); border: 1px solid var(--color-accent); background: var(--color-bg-base); color: var(--color-text-main); }
.restore-dialog::backdrop { background: color-mix(in srgb, var(--color-bg-base) 78%, transparent); }
.restore-dialog h2 { margin: .75rem 0; font-size: var(--text-xl); }
.restore-dialog p { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.dialog-actions { justify-content: end; margin-top: var(--space-6); }
@media (max-width: 640px) { .page-heading { align-items: start; flex-direction: column; } .compare-selection .instrument-button { margin-left: 0; } .detail-strip { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 360px) { .versions-page { padding-inline: 0; } .heading-actions, .version-actions, .dialog-actions { align-items: stretch; flex-direction: column; } .quiet-button, .instrument-button { width: 100%; text-align: center; } }
</style>
