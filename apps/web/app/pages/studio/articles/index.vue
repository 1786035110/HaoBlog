<template>
  <section class="studio-list" aria-labelledby="articles-title">
    <div class="list-heading">
      <div>
        <p class="instrument-label">STUDIO / OBSERVATION LOG</p>
        <h1 id="articles-title">文章日志</h1>
      </div>
      <NuxtLink class="instrument-button" to="/studio/articles/new">新建文章</NuxtLink>
    </div>

    <form class="filter-console" role="search" @submit.prevent="applyFilters">
      <label for="article-filter-keyword">标题或 Slug</label>
      <input id="article-filter-keyword" v-model="filters.keyword" maxlength="240" autocomplete="off" placeholder="输入关键词后回车">
      <label for="article-filter-status">状态</label>
      <select id="article-filter-status" v-model="filters.status">
        <option value="">全部状态</option>
        <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <button class="instrument-button" type="submit" :disabled="loading">{{ loading ? 'SCANNING…' : '筛选' }}</button>
      <button class="plain-button" type="button" @click="clearFilters">清除</button>
    </form>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="signal-note" role="status">正在读取文章信号…</p>
    <p v-else-if="!items.length" class="empty-signal">当前观测范围没有文章。</p>
    <ol v-else class="article-log">
      <li v-for="item in items" :key="item.id" class="article-entry">
        <time class="entry-time" :datetime="item.updatedAt">{{ formatDate(item.updatedAt) }}</time>
        <div class="entry-signal" aria-hidden="true" />
        <NuxtLink class="entry-body" :to="`/studio/articles/${item.id}`">
          <span class="entry-title">{{ item.title }}</span>
          <span class="entry-meta">
            <span class="status-marker"><i :data-status="item.status" aria-hidden="true" />{{ statusLabel(item.status) }}</span>
            <span>SLUG / {{ item.slug || '未设置' }}</span>
            <span>CATEGORY / {{ categoryName(item.categoryId) }}</span>
          </span>
        </NuxtLink>
      </li>
    </ol>

    <div v-if="total > pageSize" class="pagination" aria-label="文章分页">
      <button class="plain-button" type="button" :disabled="page === 0 || loading" @click="changePage(page - 1)">上一页</button>
      <span>PAGE {{ page + 1 }} / {{ Math.ceil(total / pageSize) }}</span>
      <button class="plain-button" type="button" :disabled="(page + 1) * pageSize >= total || loading" @click="changePage(page + 1)">下一页</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { computed, onMounted, reactive, ref } from 'vue'

definePageMeta({ layout: 'studio' })

type Status = components['schemas']['ArticleStatus']
const { listArticles, listCategories } = useAdminContent()
const items = ref<components['schemas']['AdminArticleSummary'][]>([])
const categories = ref<components['schemas']['CategoryResponse'][]>([])
const total = ref(0)
const page = ref(0)
const pageSize = 20
const loading = ref(false)
const errorMessage = ref('')
const filters = reactive<{ keyword: string; status: Status | '' }>({ keyword: '', status: '' })
const statusOptions: { value: Status; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'SCHEDULED', label: '已定时' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'ARCHIVED', label: '已归档' },
]
const categoryMap = computed(() => new Map(categories.value.map(category => [category.id, category.name])))

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
}

function statusLabel(status: Status) {
  return statusOptions.find(item => item.value === status)?.label || status
}

function categoryName(id?: string | null) {
  return id ? categoryMap.value.get(id) || '未知分类' : '未分类'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [result, taxonomy] = await Promise.all([
      listArticles({ page: page.value, size: pageSize, keyword: filters.keyword, status: filters.status || undefined }),
      categories.value.length ? Promise.resolve(categories.value) : listCategories(),
    ])
    items.value = result.items
    total.value = result.total
    categories.value = taxonomy
  } catch (cause) {
    errorMessage.value = cause instanceof Error ? cause.message : '文章列表暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function applyFilters() { page.value = 0; await load() }
async function clearFilters() { filters.keyword = ''; filters.status = ''; page.value = 0; await load() }
async function changePage(next: number) { page.value = next; await load() }

onMounted(load)
</script>

<style scoped>
.studio-list { max-width: 76rem; margin: 0 auto; }
.list-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-8); }
.list-heading h1 { margin: .8rem 0 0; font-size: clamp(2.5rem, 8vw, 6rem); }
.instrument-button { display: inline-block; width: fit-content; padding: .7rem 1rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); letter-spacing: .08em; text-decoration: none; }
.instrument-button:hover, .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-bg-base); }
.instrument-button:disabled { cursor: wait; opacity: .55; }
.filter-console { display: grid; grid-template-columns: auto minmax(12rem, 1fr) auto minmax(10rem, 13rem) auto auto; align-items: center; gap: var(--space-3); margin-bottom: var(--space-8); padding: var(--space-3) 0; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.filter-console label { color: var(--color-text-muted); font: var(--text-xs)/1.3 var(--font-mono); }
.filter-console input, .filter-console select { min-width: 0; padding: .55rem 0; border: 0; border-bottom: 1px solid var(--color-border); outline: 0; background: transparent; color: var(--color-text-main); font: var(--text-sm)/1.3 var(--font-body); }
.filter-console input:focus, .filter-console select:focus { border-bottom-color: var(--color-accent); }
.plain-button { padding: .4rem 0; border: 0; background: transparent; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); cursor: pointer; }
.plain-button:hover:not(:disabled), .plain-button:focus-visible { color: var(--color-accent); }
.plain-button:disabled { cursor: not-allowed; opacity: .5; }
.article-log { position: relative; display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.article-log::before { position: absolute; top: 0; bottom: 0; left: clamp(5.2rem, 13vw, 9rem); width: 1px; background: var(--color-border); content: ''; }
.article-entry { position: relative; display: grid; grid-template-columns: clamp(4.6rem, 12vw, 8.5rem) 1rem minmax(0, 1fr); gap: var(--space-3); min-height: 7rem; padding: var(--space-4) 0; }
.entry-time { padding-top: .2rem; color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.entry-signal { z-index: 1; width: .5rem; height: .5rem; margin-top: .35rem; border: 1px solid var(--color-accent); border-radius: 50%; background: var(--color-bg-base); box-shadow: 0 0 0 3px var(--color-bg-base); }
.entry-body { display: grid; gap: .6rem; min-width: 0; padding: .1rem 0 .7rem; border-bottom: 1px solid color-mix(in srgb, var(--color-border) 65%, transparent); text-decoration: none; }
.entry-title { color: var(--color-text-main); font: clamp(1.05rem, 2vw, 1.35rem)/1.35 var(--font-display); }
.entry-body:hover .entry-title, .entry-body:focus-visible .entry-title { color: var(--color-accent); }
.entry-meta { display: flex; flex-wrap: wrap; gap: .4rem 1rem; color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.status-marker { display: inline-flex; align-items: center; gap: .4rem; }
.status-marker i { width: 4px; height: 4px; border-radius: 50%; background: var(--color-text-muted); }
.status-marker i[data-status='DRAFT'], .status-marker i[data-status='SCHEDULED'] { background: var(--color-warn); }
.status-marker i[data-status='PUBLISHED'] { background: var(--color-accent); }
.empty-signal, .signal-note, .form-error { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.form-error { color: var(--color-warn); }
.pagination { display: flex; align-items: center; justify-content: center; gap: var(--space-6); margin-top: var(--space-8); color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
@media (max-width: 760px) { .filter-console { grid-template-columns: auto minmax(0, 1fr); } .filter-console .instrument-button, .filter-console .plain-button { justify-self: start; } }
@media (max-width: 360px) { .list-heading { align-items: start; flex-direction: column; } .filter-console { gap: var(--space-2); } .article-entry { grid-template-columns: 3.7rem .75rem minmax(0, 1fr); gap: .55rem; } .entry-meta { display: grid; gap: .25rem; } .article-log::before { left: 4.05rem; } }
</style>
