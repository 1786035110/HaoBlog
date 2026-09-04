<template>
  <section class="moderation-scene" aria-labelledby="comments-title">
    <div class="page-heading">
      <div>
        <p class="instrument-label">STUDIO / COMMENT SIGNAL</p>
        <h1 id="comments-title">评论管理</h1>
      </div>
      <p class="signal-note">{{ list?.total ?? 0 }} 条记录 · {{ page + 1 }} / {{ pageCount }}</p>
    </div>

    <form class="filter-bar" @submit.prevent="applyFilters">
      <label>状态
        <select v-model="filters.status" aria-label="按状态筛选">
          <option value="">全部</option>
          <option value="PENDING">历史待处理</option>
          <option value="APPROVED">已通过</option>
          <option value="SPAM">垃圾</option>
          <option value="REJECTED">已拒绝</option>
          <option value="USER_DELETED">用户删除</option>
        </select>
      </label>
      <label>文章
        <select v-model="filters.articleId" aria-label="按文章筛选">
          <option value="">全部文章</option>
          <option v-for="article in articles" :key="article.id" :value="article.id">{{ article.title }}</option>
        </select>
      </label>
      <label class="keyword-field">关键词
        <input v-model="filters.keyword" maxlength="240" placeholder="昵称或内容" aria-label="按关键词筛选">
      </label>
      <label>排序
        <select v-model="filters.direction" aria-label="时间排序">
          <option value="desc">最新在前</option>
          <option value="asc">最早在前</option>
        </select>
      </label>
      <button class="instrument-button" type="submit">重新扫描</button>
    </form>

    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="signal-note" role="status">正在读取评论记录…</p>
    <div v-else class="log-layout">
      <div class="log-table-wrap">
        <table class="log-table">
          <caption class="sr-only">评论管理记录</caption>
          <thead><tr><th>时间</th><th>状态</th><th>信号</th><th>文章 ID</th><th>邮箱</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in list?.items || []" :key="item.id" :data-active="selected?.id === item.id">
              <td class="mono">{{ formatDate(item.createdAt) }}</td>
              <td><span class="status-mark" :data-status="item.status">{{ statusLabel(item.status) }}</span></td>
              <td><button class="log-link" type="button" @click="select(item.id)">{{ item.nickname }}：{{ item.content }}</button></td>
              <td class="mono">{{ item.articleId.slice(0, 8) }}</td>
              <td class="mono">{{ item.emailMasked || '—' }}</td>
              <td><button class="quiet-button" type="button" @click="select(item.id)">详情</button></td>
            </tr>
            <tr v-if="!list?.items.length"><td colspan="6" class="empty-row">没有匹配的审核信号。</td></tr>
          </tbody>
        </table>
      </div>

      <aside v-if="selected" class="detail-panel" aria-labelledby="comment-detail-title">
        <p class="instrument-label">COMMENT / DETAIL</p>
        <h2 id="comment-detail-title">{{ selected.nickname }}</h2>
        <dl class="detail-grid">
          <div><dt>邮箱</dt><dd>{{ selected.email || '未提供' }}</dd></div>
          <div><dt>文章</dt><dd class="mono">{{ selected.articleId }}</dd></div>
          <div><dt>提交</dt><dd>{{ formatDate(selected.createdAt) }}</dd></div>
          <div><dt>版本</dt><dd class="mono">{{ selected.version }}</dd></div>
        </dl>
        <blockquote>{{ selected.content || '（内容已清除）' }}</blockquote>
        <form class="moderation-form" @submit.prevent="submitModeration">
          <label for="moderation-status">写入状态</label>
          <select id="moderation-status" v-model="moderation.status">
            <option value="APPROVED">通过</option>
            <option value="SPAM">垃圾</option>
            <option value="REJECTED">拒绝</option>
          </select>
          <label for="moderation-reason">审核理由</label>
          <textarea id="moderation-reason" v-model="moderation.reason" maxlength="600" rows="4" placeholder="可选；会写入审核记录" />
          <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
          <button class="instrument-button" type="submit" :disabled="saving || selected.status === 'USER_DELETED'">
            {{ saving ? 'WRITING…' : '写入审核结果' }}
          </button>
        </form>
      </aside>
    </div>

    <nav v-if="pageCount > 1" class="pagination" aria-label="评论分页">
      <button class="quiet-button" type="button" :disabled="page === 0" @click="goPage(page - 1)">上一页</button>
      <span class="signal-note">PAGE {{ page + 1 }} / {{ pageCount }}</span>
      <button class="quiet-button" type="button" :disabled="page + 1 >= pageCount" @click="goPage(page + 1)">下一页</button>
    </nav>
  </section>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { computed, onMounted, reactive, ref } from 'vue'
import type { CommentDetail, CommentList } from '../../composables/useAdminComments'

definePageMeta({ layout: 'studio' })

const { listComments, getComment, moderateComment } = useAdminComments()
const { listArticles } = useAdminContent()
const list = ref<CommentList | null>(null)
const articles = ref<components['schemas']['AdminArticleSummary'][]>([])
const selected = ref<CommentDetail | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const actionError = ref('')
const page = ref(0)
const filters = reactive<{ status: components['schemas']['CommentStatus'] | ''; articleId: string; keyword: string; direction: 'asc' | 'desc' }>({ status: '', articleId: '', keyword: '', direction: 'desc' })
const moderation = reactive<{ status: 'APPROVED' | 'SPAM' | 'REJECTED'; reason: string }>({ status: 'APPROVED', reason: '' })
const pageCount = computed(() => Math.max(1, Math.ceil((list.value?.total || 0) / (list.value?.size || 20))))

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = await listComments({ page: page.value, size: 20, status: filters.status || undefined, articleId: filters.articleId || undefined, keyword: filters.keyword, direction: filters.direction })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '审核日志暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function select(id: string) {
  actionError.value = ''
  try {
    selected.value = await getComment(id)
    moderation.status = selected.value.status === 'PENDING' ? 'APPROVED' : (selected.value.status === 'USER_DELETED' ? 'REJECTED' : selected.value.status)
    moderation.reason = selected.value.moderationReason || ''
  } catch (cause) {
    actionError.value = cause instanceof Error ? cause.message : '评论详情暂时不可用。'
  }
}

async function submitModeration() {
  if (!selected.value || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    selected.value = await moderateComment(selected.value.id, { version: selected.value.version, status: moderation.status, reason: moderation.reason.trim() || null })
    await load()
  } catch (cause) {
    actionError.value = cause instanceof Error ? cause.message : '审核结果写入失败。'
  } finally {
    saving.value = false
  }
}

function applyFilters() { page.value = 0; void load() }
function goPage(next: number) { page.value = next; void load() }
function formatDate(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
function statusLabel(value: components['schemas']['CommentStatus']) { return ({ PENDING: '待审', APPROVED: '通过', SPAM: '垃圾', REJECTED: '拒绝', USER_DELETED: '已删' } as Record<string, string>)[value] }

onMounted(async () => {
  const [articleResult] = await Promise.all([listArticles({ page: 0, size: 50, direction: 'asc' }), load()])
  articles.value = articleResult.items
})
</script>

<style scoped>
.moderation-scene { max-width: 100rem; margin: 0 auto; }
.page-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-6); }
.page-heading h1 { margin: .8rem 0 0; font-size: clamp(2.2rem, 7vw, 5rem); }
.filter-bar { display: flex; flex-wrap: wrap; align-items: end; gap: var(--space-3); margin-bottom: var(--space-4); padding: var(--space-3) 0; border-block: 1px solid var(--color-border); }
.filter-bar label, .moderation-form label { display: grid; gap: .35rem; color: var(--color-accent); font: var(--text-xs)/1.2 var(--font-mono); }
.filter-bar select, .filter-bar input, .moderation-form select, .moderation-form textarea { min-width: 8rem; padding: .55rem .6rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); font: var(--text-sm)/1.3 var(--font-body); }
.keyword-field { flex: 1 1 14rem; }
.keyword-field input { width: 100%; }
.log-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(18rem, 25rem); gap: var(--space-6); align-items: start; }
.log-table-wrap { overflow-x: auto; border: 1px solid var(--color-border); }
.log-table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
.log-table th, .log-table td { padding: .75rem; border-bottom: 1px solid var(--color-border); text-align: left; vertical-align: top; }
.log-table th { color: var(--color-accent); font: var(--text-xs)/1.2 var(--font-mono); letter-spacing: .06em; white-space: nowrap; }
.log-table tr[data-active='true'] { background: color-mix(in srgb, var(--color-accent) 8%, transparent); }
.mono { font-family: var(--font-mono); font-size: var(--text-xs); }
.log-link { max-width: 26rem; padding: 0; border: 0; background: transparent; color: var(--color-text-main); text-align: left; cursor: pointer; }
.log-link:hover, .log-link:focus-visible { color: var(--color-accent); }
.status-mark { color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); white-space: nowrap; }
.status-mark[data-status='PENDING'] { color: var(--color-warn); }
.status-mark[data-status='APPROVED'] { color: var(--color-accent); }
.detail-panel { padding: var(--space-4); border-left: 2px solid var(--color-accent); background: linear-gradient(90deg, color-mix(in srgb, var(--color-accent) 7%, transparent), transparent 80%); }
.detail-panel h2 { margin: .7rem 0 1rem; font-size: var(--text-lg); }
.detail-grid { display: grid; gap: .55rem; margin: 0 0 var(--space-4); font: var(--text-xs)/1.4 var(--font-mono); }
.detail-grid div { display: grid; grid-template-columns: 4rem 1fr; gap: .5rem; }
.detail-grid dt { color: var(--color-accent); }
.detail-grid dd { min-width: 0; margin: 0; overflow-wrap: anywhere; color: var(--color-text-muted); }
blockquote { margin: 0 0 var(--space-4); padding: var(--space-3); border-left: 1px solid var(--color-border); color: var(--color-text-main); white-space: pre-wrap; overflow-wrap: anywhere; }
.moderation-form { display: grid; gap: .6rem; }
.moderation-form textarea { resize: vertical; }
.instrument-button, .quiet-button { width: fit-content; padding: .65rem .8rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }
.quiet-button { border-color: var(--color-border); color: var(--color-text-muted); }
.instrument-button:hover:not(:disabled), .instrument-button:focus-visible, .quiet-button:hover:not(:disabled), .quiet-button:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
button:disabled { cursor: wait; opacity: .55; }
.pagination { display: flex; justify-content: end; align-items: center; gap: var(--space-3); margin-top: var(--space-4); }
.empty-row { padding: 2rem !important; color: var(--color-text-muted); text-align: center !important; font-family: var(--font-mono); }
.form-error { color: var(--color-warn); font: var(--text-sm)/1.5 var(--font-mono); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }
@media (max-width: 800px) { .log-layout { grid-template-columns: 1fr; } .detail-panel { border-top: 2px solid var(--color-accent); border-left: 0; } }
@media (max-width: 360px) { .page-heading { align-items: start; flex-direction: column; } .filter-bar { display: grid; grid-template-columns: 1fr; } .filter-bar label, .filter-bar select, .filter-bar input { width: 100%; } .log-table th, .log-table td { padding: .55rem; } }
</style>
