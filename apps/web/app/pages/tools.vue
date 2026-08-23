<script setup lang="ts">
import { provide } from 'vue'
import type { components } from '@haoblog/api-client'
import { embeddedWorkerKey, useEmbeddedWorker } from '~/composables/useEmbeddedWorker'
import { getEmbeddedToolComponent } from '~/utils/embeddedTools'

type PublicTools = components['schemas']['PublicToolListResponse']
type PublicTool = components['schemas']['PublicToolResponse']
type SortMode = 'stable' | 'favorites' | 'frequent'

useHead({
  title: '工具目录｜极夜观测站',
  meta: [{ name: 'description', content: '极夜观测站的公共工具目录：按分类、类型和关键词定位可用工具。' }],
})

const emptyTools: PublicTools = { items: [], categories: [] }
const { data, error } = await usePublicApi<PublicTools>('/api/v1/public/tools', {
  key: 'public-tools',
  default: () => emptyTools,
})

const route = useRoute()
const selectedToolSlug = computed(() => typeof route.query.tool === 'string' ? route.query.tool : '')
const category = ref('')
const type = ref('')
const keyword = ref('')
const sortMode = ref<SortMode>('stable')
const favorites = ref(new Set<string>())
const usage = ref<Record<string, { count: number; lastUsed: string }>>({})
const embeddedWorker = useEmbeddedWorker()
provide(embeddedWorkerKey, embeddedWorker)

const tools = computed(() => data.value?.items || [])
const categories = computed(() => data.value?.categories || [])
const expanded = ref(new Set<string>(tools.value.filter(tool => tool.slug === selectedToolSlug.value).map(tool => tool.id)))
const filteredTools = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  const result = tools.value.filter((tool) => {
    if (category.value && tool.category.slug !== category.value) return false
    if (type.value && tool.type !== type.value) return false
    if (!query) return true
    return [tool.title, tool.slug, tool.description || '', tool.category.name, ...tool.tags]
      .join(' ')
      .toLocaleLowerCase()
      .includes(query)
  })
  if (sortMode.value === 'stable') return result
  const mode = sortMode.value as Exclude<SortMode, 'stable'>
  return [...result].sort((left, right) => compareTools(left, right, mode))
})

onMounted(() => {
  favorites.value = readFavorites()
  usage.value = readUsage()
})

watch([selectedToolSlug, tools], async () => {
  if (!import.meta.client) return
  const slug = selectedToolSlug.value
  if (!slug) return
  const tool = tools.value.find(item => item.slug === slug)
  if (!tool) return
  expanded.value = new Set([...expanded.value, tool.id])
  await nextTick()
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById(`tool-${tool.slug}`)?.scrollIntoView({ block: 'center', behavior: reducedMotion ? 'auto' : 'smooth' })
}, { immediate: true })

function compareTools(left: PublicTool, right: PublicTool, mode: Exclude<SortMode, 'stable'>) {
  const leftUsage = usage.value[left.id] || { count: 0, lastUsed: '' }
  const rightUsage = usage.value[right.id] || { count: 0, lastUsed: '' }
  if (mode === 'favorites') {
    const favoriteOrder = Number(favorites.value.has(right.id)) - Number(favorites.value.has(left.id))
    if (favoriteOrder) return favoriteOrder
  }
  const countOrder = rightUsage.count - leftUsage.count
  if (countOrder) return countOrder
  const recentOrder = rightUsage.lastUsed.localeCompare(leftUsage.lastUsed)
  if (recentOrder) return recentOrder
  return stableToolOrder(left, right)
}

function stableToolOrder(left: PublicTool, right: PublicTool) {
  return left.sortOrder - right.sortOrder || left.title.localeCompare(right.title) || left.id.localeCompare(right.id)
}

function toggleFavorite(tool: PublicTool) {
  const next = new Set(favorites.value)
  if (next.has(tool.id)) next.delete(tool.id)
  else next.add(tool.id)
  favorites.value = next
  localStorage.setItem('haoblog-tool-favorites', JSON.stringify([...next]))
}

function toggleTool(tool: PublicTool) {
  const next = new Set(expanded.value)
  if (next.has(tool.id)) next.delete(tool.id)
  else {
    next.add(tool.id)
    markUsed(tool)
  }
  expanded.value = next
}

function markUsed(tool: PublicTool) {
  const next = { ...usage.value, [tool.id]: { count: (usage.value[tool.id]?.count || 0) + 1, lastUsed: new Date().toISOString() } }
  usage.value = next
  localStorage.setItem('haoblog-tool-usage', JSON.stringify(next))
}

function readFavorites() {
  try {
    const value: unknown = JSON.parse(localStorage.getItem('haoblog-tool-favorites') || '[]')
    return new Set(Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [])
  } catch { return new Set<string>() }
}

function readUsage() {
  try {
    const value: unknown = JSON.parse(localStorage.getItem('haoblog-tool-usage') || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter(([, item]) => {
      return item && typeof item === 'object' && typeof (item as { count?: unknown }).count === 'number' && typeof (item as { lastUsed?: unknown }).lastUsed === 'string'
    })) as Record<string, { count: number; lastUsed: string }>
  } catch { return {} }
}

function safeExternalUrl(url: string | null) {
  if (!url) return null
  try {
    const value = new URL(url)
    return value.protocol === 'https:' && !value.username && !value.password ? value.toString() : null
  } catch { return null }
}
</script>

<template>
  <section class="tools-scene" aria-labelledby="tools-title">
    <header class="tools-heading">
      <div>
        <p class="instrument-label">TOOLBOX / SWITCHBOARD / PUBLIC SIGNAL</p>
        <h1 id="tools-title">工具目录</h1>
        <p class="signal-copy">把常用的工程信号收进一台安静的控制台。选择分类、类型或关键词，展开一行即可查看入口。</p>
      </div>
      <p class="console-readout" aria-label="工具目录状态"><span class="status-light" aria-hidden="true" /> {{ filteredTools.length }} SIGNALS / ACTIVE ONLY</p>
    </header>

    <form class="tool-filters" aria-label="工具目录筛选" @submit.prevent>
      <label>分类<select v-model="category"><option value="">全部分类</option><option v-for="item in categories" :key="item.id" :value="item.slug">{{ item.name }}</option></select></label>
      <label>类型<select v-model="type"><option value="">全部类型</option><option value="EMBEDDED">EMBEDDED</option><option value="LINK">LINK</option><option value="SHOWCASE">SHOWCASE</option></select></label>
      <label class="keyword-filter">关键词<input v-model="keyword" type="search" placeholder="title / tag / command" autocomplete="off"></label>
      <div class="sort-controls" role="group" aria-label="工具排序">
        <span class="filter-label">排序</span>
        <button type="button" :aria-pressed="sortMode === 'stable'" @click="sortMode = 'stable'">默认</button>
        <button type="button" :aria-pressed="sortMode === 'favorites'" @click="sortMode = 'favorites'">收藏优先</button>
        <button type="button" :aria-pressed="sortMode === 'frequent'" @click="sortMode = 'frequent'">最常用</button>
      </div>
    </form>

    <p v-if="error" class="tool-empty" role="status">工具目录暂时无法接入，请稍后重试。</p>
    <div v-else-if="!tools.length" class="tool-empty" role="status">
      <span class="empty-code">NO ACTIVE SIGNAL</span>
      <strong>当前没有可公开使用的工具。</strong>
      <span>目录将在工具完成校准后重新开放。</span>
    </div>
    <div v-else-if="!filteredTools.length" class="tool-empty" role="status">
      <span class="empty-code">FILTER / 0</span>
      <strong>没有匹配的工具信号。</strong>
      <span>尝试清除一个筛选条件。</span>
    </div>
    <ol v-else class="tool-console" aria-label="公共工具目录">
      <li v-for="(tool, index) in filteredTools" :id="`tool-${tool.slug}`" :key="tool.id" class="tool-entry" :data-expanded="expanded.has(tool.id)">
        <div class="tool-row">
          <span class="tool-index" aria-hidden="true">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="tool-signal" aria-hidden="true" />
          <div class="tool-identity">
            <strong>{{ tool.title }}</strong>
            <span>{{ tool.slug }}</span>
          </div>
          <div class="tool-metadata">
            <span>{{ tool.category.name }}</span>
            <span>{{ tool.type }} / ACTIVE</span>
            <span v-for="tag in tool.tags" :key="`${tool.id}-${tag}`">#{{ tag }}</span>
          </div>
          <button class="tool-favorite" type="button" :aria-pressed="favorites.has(tool.id)" :aria-label="`${favorites.has(tool.id) ? '取消收藏' : '收藏'} ${tool.title}`" @click="toggleFavorite(tool)">{{ favorites.has(tool.id) ? 'FAV' : 'SAVE' }}</button>
          <button class="tool-unfold" type="button" :aria-expanded="expanded.has(tool.id)" :aria-controls="`tool-panel-${tool.slug}`" @click="toggleTool(tool)">{{ expanded.has(tool.id) ? 'CLOSE' : 'UNFOLD' }}</button>
        </div>
        <p class="tool-summary">{{ tool.description || '暂无观测说明。' }}</p>
        <div v-if="expanded.has(tool.id)" :id="`tool-panel-${tool.slug}`" class="tool-panel" role="region" :aria-label="`${tool.title} 工具面板`">
          <p class="tool-description">{{ tool.description || '暂无观测说明。' }}</p>
          <div class="tool-tags" aria-label="工具标签"><span v-for="tag in tool.tags" :key="tag">#{{ tag }}</span></div>
          <template v-if="tool.type === 'EMBEDDED'">
            <div class="embedded-slot" :data-component-key="tool.componentKey || undefined">
              <span class="slot-marker" aria-hidden="true">/</span>
              <strong>内嵌组件槽位已建立 · 浏览器本地运行</strong>
              <span>输入不会上传；组件按白名单 componentKey 懒加载。</span>
              <component :is="getEmbeddedToolComponent(tool.componentKey)" v-if="getEmbeddedToolComponent(tool.componentKey)" />
              <span v-else class="tool-warning">此工具组件未通过白名单校验，已停止装载。</span>
            </div>
          </template>
          <template v-else>
            <a v-if="safeExternalUrl(tool.url)" class="external-link" :href="safeExternalUrl(tool.url) || undefined" target="_blank" rel="noopener noreferrer external" @click="markUsed(tool)">访问 {{ tool.type === 'SHOWCASE' ? '项目展示' : '外部工具' }} <span aria-hidden="true">↗</span></a>
            <p v-else class="tool-warning">外链未通过 HTTPS 校验，已停止访问。</p>
          </template>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.tools-scene { max-width: 74rem; margin: 8vh auto 4rem; }
.tools-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-8); margin-bottom: clamp(2rem, 7vw, 5rem); }
.tools-heading h1 { max-width: none; margin-bottom: 1.4rem; }
.signal-copy { max-width: 42rem; }
.console-readout, .tool-index, .tool-metadata, .tool-favorite, .tool-unfold, .filter-label, .sort-controls button, .tool-identity span, .tool-tags, .empty-code, .embedded-slot, .external-link, .tool-warning { font: var(--text-xs)/1.4 var(--font-mono); letter-spacing: .04em; }
.console-readout { display: inline-flex; align-items: center; gap: .55rem; flex: 0 0 auto; margin: 0 0 .5rem; color: var(--color-text-muted); }
.status-light, .tool-signal { display: inline-block; width: .45rem; height: .45rem; border-radius: 50%; background: var(--color-accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-accent) 14%, transparent); }
.tool-filters { display: grid; grid-template-columns: minmax(10rem, 1fr) minmax(10rem, 1fr) minmax(14rem, 2fr) auto; align-items: end; gap: var(--space-3); padding: .8rem 0; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.tool-filters label { display: grid; gap: .35rem; color: var(--color-text-muted); font: var(--text-xs)/1.2 var(--font-mono); }
.tool-filters select, .tool-filters input { width: 100%; min-height: 2.4rem; padding: .45rem .55rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); font: var(--text-sm)/1.2 var(--font-mono); }
.tool-filters input::placeholder { color: var(--color-text-muted); opacity: .7; }
.sort-controls { display: flex; align-items: center; gap: .3rem; min-height: 2.4rem; }
.filter-label { color: var(--color-text-muted); margin-right: .2rem; }
.sort-controls button { padding: .45rem .5rem; border: 1px solid var(--color-border); background: transparent; color: var(--color-text-muted); cursor: pointer; }
.sort-controls button[aria-pressed='true'], .sort-controls button:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.tool-console { display: grid; gap: 1px; margin: 2rem 0 0; padding: 0; list-style: none; background: var(--color-border); }
.tool-entry { background: var(--color-bg-base); }
.tool-entry[data-expanded='true'] { background: color-mix(in srgb, var(--color-accent) 5%, var(--color-bg-base)); }
.tool-row { display: grid; grid-template-columns: 2.25rem .5rem minmax(10rem, 1.5fr) minmax(10rem, 1fr) auto auto; align-items: center; gap: .8rem; min-height: 4.4rem; padding: .75rem .9rem; }
.tool-index, .tool-identity span, .tool-metadata { color: var(--color-text-muted); }
.tool-identity { display: grid; gap: .25rem; min-width: 0; }
.tool-identity strong { overflow: hidden; color: var(--color-text-main); font: 700 clamp(1rem, 2vw, 1.25rem)/1.2 var(--font-display); letter-spacing: -.02em; text-overflow: ellipsis; white-space: nowrap; }
.tool-identity span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tool-metadata { display: flex; flex-wrap: wrap; gap: .3rem .8rem; }
.tool-metadata span + span { color: var(--color-accent); }
.tool-favorite, .tool-unfold { border: 1px solid var(--color-border); padding: .45rem .55rem; background: transparent; color: var(--color-text-muted); cursor: pointer; white-space: nowrap; }
.tool-favorite[aria-pressed='true'], .tool-favorite:hover, .tool-unfold:hover, .tool-favorite:focus-visible, .tool-unfold:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.tool-unfold { color: var(--color-accent); }
.tool-summary { margin: 0; padding: 0 .9rem .8rem 4.3rem; color: var(--color-text-muted); line-height: 1.5; }
.tool-panel { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .8rem 1rem; padding: .25rem .9rem 1.2rem 4.3rem; border-top: 1px solid var(--color-border-soft); }
.tool-description { grid-column: 1 / -1; max-width: 60rem; margin: .7rem 0 0; color: var(--color-text-main); line-height: 1.65; }
.tool-tags { display: flex; flex-wrap: wrap; gap: .4rem .7rem; color: var(--color-text-muted); }
.embedded-slot { display: grid; grid-template-columns: auto 1fr; gap: .15rem .6rem; align-items: baseline; min-width: min(100%, 32rem); padding: .8rem; border: 1px dashed var(--color-border); color: var(--color-text-muted); }
.embedded-slot strong { color: var(--color-accent); font-weight: 400; }
.embedded-slot span:last-child { grid-column: 2; }
.slot-marker { color: var(--color-warn); font-size: 1.2rem; }
.external-link { justify-self: end; padding: .55rem .75rem; border: 1px solid var(--color-accent); color: var(--color-accent); text-decoration: none; }
.external-link:hover, .external-link:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
.tool-warning { color: var(--color-warn); }
.tool-empty { display: grid; gap: .6rem; max-width: 38rem; margin: 2rem 0; padding: 1.5rem; border: 1px dashed var(--color-border); color: var(--color-text-muted); }
.tool-empty strong { color: var(--color-text-main); font: 700 var(--text-lg)/1.2 var(--font-display); }
.empty-code { color: var(--color-warn); }
@media (max-width: 960px) { .tool-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .keyword-filter { grid-column: span 2; } .sort-controls { justify-content: end; } .tool-row { grid-template-columns: 2.25rem .5rem minmax(8rem, 1fr) auto auto; } .tool-metadata { grid-column: 3 / -1; grid-row: 2; } }
@media (max-width: 640px) { .tools-scene { margin-top: 5vh; } .tools-heading { align-items: start; flex-direction: column; gap: 1rem; margin-bottom: 2.5rem; } .tools-heading h1 { font-size: clamp(3rem, 18vw, 5rem); } .tool-filters { grid-template-columns: 1fr; } .keyword-filter { grid-column: auto; } .sort-controls { justify-content: start; flex-wrap: wrap; } .tool-row { grid-template-columns: 1.8rem .45rem minmax(0, 1fr) auto; gap: .55rem; padding-inline: .55rem; } .tool-metadata { grid-column: 3 / -1; grid-row: 2; } .tool-favorite { grid-column: 3; grid-row: 3; justify-self: start; } .tool-unfold { grid-column: 4; grid-row: 3; } .tool-summary { padding-left: 2.7rem; padding-right: .55rem; } .tool-panel { grid-template-columns: 1fr; padding-left: 2.7rem; padding-right: .55rem; } .external-link { justify-self: start; } }
@media (max-width: 360px) { .tools-scene { margin-top: 2rem; } .tool-row { grid-template-columns: 1.6rem .4rem minmax(0, 1fr); } .tool-favorite, .tool-unfold { grid-column: 3; grid-row: auto; justify-self: start; } .tool-unfold { margin-top: .2rem; } .tool-metadata { grid-column: 3; grid-row: auto; } .tool-summary { padding-left: 2.2rem; } .tool-panel { padding-left: 2.2rem; } }
@media (prefers-reduced-motion: reduce) { .tools-scene *, .tools-scene *::before, .tools-scene *::after { transition: none !important; animation: none !important; } }
</style>
