<script setup lang="ts">
import { emptyGardenGraph, gardenNodeDate, gardenNodeTypeLabel, sortedGardenTimeline, type GardenGraph } from '~/utils/publicGarden'
import { defaultPublicSite, usePublicSite } from '~/utils/publicSite'
import { buildPublicPageSeo, publicPageHead } from '~/utils/publicArticleSeo'

const [{ data: site, error: siteError }, { data: graph, error: graphError }] = await Promise.all([
  usePublicSite(),
  usePublicApi<GardenGraph>('/api/v1/public/garden', {
    key: 'public-garden',
    default: () => emptyGardenGraph,
  }),
])

const resolvedSite = site.value || defaultPublicSite
const resolvedGraph = computed(() => graph.value || emptyGardenGraph)
const timeline = computed(() => sortedGardenTimeline(resolvedGraph.value.nodes))
const canvasAllowed = ref(false)
const { enabled: saveData } = useDataSaver()

useHead(() => publicPageHead(buildPublicPageSeo(
  resolvedSite,
  '/garden',
  `知识星图 · ${resolvedSite.title}`,
  '沿着文章与工具的公开关系，浏览极夜观测站正在生长的知识时间线。',
)))

onMounted(() => {
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const coarsePointer = window.matchMedia('(pointer: coarse)').matches
  const smallViewport = window.matchMedia('(max-width: 767px)').matches
  const canvas = document.createElement('canvas')
  canvasAllowed.value = !saveData.value && !reducedMotion && !coarsePointer && !smallViewport && !connection?.saveData
    && Boolean(canvas.getContext('2d'))
})

function typeLabel(type: GardenGraph['nodes'][number]['type']) {
  return gardenNodeTypeLabel(type)
}

function nodeDate(node: GardenGraph['nodes'][number]) {
  return gardenNodeDate(node)
}
</script>

<template>
  <section class="garden-scene" aria-labelledby="garden-title">
    <header class="garden-heading">
      <div>
        <p class="instrument-label">DIGITAL GARDEN / KNOWLEDGE SIGNAL</p>
        <h1 id="garden-title">知识星图</h1>
        <p class="signal-copy">把公开文章与工具放回它们的轨道。时间线先抵达，桌面星图只在设备允许时接入。</p>
      </div>
      <p class="garden-readout" aria-label="星图状态"><span class="status-light" aria-hidden="true" /> {{ resolvedGraph.nodes.length }} NODES / {{ resolvedGraph.edges.length }} EDGES</p>
    </header>

    <p v-if="siteError" class="garden-note" role="status">站点信息暂时不可用，当前仍保留知识内容入口。</p>
    <p v-if="graphError" class="garden-empty" role="status">
      <strong>图谱信号暂时离线。</strong>
      <span>接口没有返回关系数据；你仍可以从下方时间线继续阅读。</span>
    </p>
    <p v-else-if="!resolvedGraph.nodes.length" class="garden-empty" role="status">
      <strong>星图还没有公开节点。</strong>
      <span>当文章或工具完成公开校准后，它们会在这里留下轨道。</span>
    </p>

    <GardenCanvas v-if="canvasAllowed && !graphError && resolvedGraph.nodes.length" :graph="resolvedGraph" />

    <section class="garden-timeline" aria-labelledby="garden-timeline-title">
      <div class="timeline-heading">
        <p class="instrument-label">PUBLIC SNAPSHOT / SSR TIMELINE</p>
        <h2 id="garden-timeline-title">可读的知识时间线</h2>
        <p>每一行都来自当前公开快照或 ACTIVE 工具；节点类型、日期和站内入口同时保留。</p>
      </div>
      <ol v-if="timeline.length" class="garden-log">
        <li v-for="node in timeline" :key="node.id" class="garden-log-entry">
          <div class="garden-log-meta">
            <span class="node-type">{{ typeLabel(node.type) }}</span>
            <time :datetime="node.publishedAt || undefined">{{ nodeDate(node) }}</time>
            <span>DEGREE {{ node.degree }}</span>
          </div>
          <div class="garden-log-body">
            <a :href="node.href" class="garden-log-link">{{ node.label }} <span aria-hidden="true">→</span></a>
            <p>{{ node.summary || (node.type === 'TOOL' ? 'ACTIVE 工具入口，浏览器内按需展开。' : '公开文章快照，进入正文继续观测。') }}</p>
          </div>
        </li>
      </ol>
      <p v-else class="garden-empty garden-empty--timeline" role="status">
        <strong>当前没有可读的文章或工具时间线。</strong>
        <span>图谱为空不影响站点导航；请稍后再来查看公开内容。</span>
      </p>
    </section>
  </section>
</template>

<style scoped>
.garden-scene { max-width: 78rem; margin: 8vh auto 4rem; }
.garden-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-8); margin-bottom: clamp(2.5rem, 8vw, 6rem); }
.garden-heading h1 { max-width: none; margin: 1rem 0 1.25rem; }
.garden-heading .signal-copy { max-width: 42rem; }
.garden-readout, .garden-log-meta, .node-type { color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); letter-spacing: .04em; }
.garden-readout { display: inline-flex; align-items: center; gap: .55rem; flex: 0 0 auto; margin: 0 0 .55rem; }
.status-light { display: inline-block; width: .45rem; height: .45rem; border-radius: 50%; background: var(--color-accent); }
.garden-note { margin: 1rem 0; color: var(--color-warn); font: var(--text-sm)/1.5 var(--font-mono); }
.garden-empty { display: grid; gap: .5rem; max-width: 42rem; margin: 1rem 0 2rem; padding: 1rem 1.2rem; border-left: 2px solid var(--color-warn); background: color-mix(in srgb, var(--color-warn) 7%, transparent); color: var(--color-text-muted); }
.garden-empty strong { color: var(--color-text-main); }
.garden-empty--timeline { margin-top: 1.25rem; }
.garden-timeline { margin-top: clamp(3rem, 10vw, 8rem); }
.timeline-heading { display: grid; gap: .7rem; max-width: 44rem; margin-bottom: 2rem; }
.timeline-heading h2 { margin: 0; color: var(--color-text-main); font-family: var(--font-display); font-size: clamp(2rem, 5vw, 4rem); letter-spacing: -.05em; }
.timeline-heading p:last-child { margin: 0; color: var(--color-text-muted); line-height: 1.7; }
.garden-log { display: grid; gap: 1px; margin: 0; padding: 0; list-style: none; background: var(--color-border); }
.garden-log-entry { display: grid; grid-template-columns: minmax(9rem, 14rem) minmax(0, 1fr); gap: var(--space-6); padding: 1.1rem 1rem; background: var(--color-bg-base); }
.garden-log-meta { display: grid; align-content: start; gap: .45rem; padding-top: .18rem; }
.node-type { color: var(--color-accent); }
.garden-log-body { display: grid; gap: .35rem; }
.garden-log-link { width: fit-content; color: var(--color-text-main); font: 700 clamp(1.1rem, 2vw, 1.45rem)/1.2 var(--font-display); text-decoration: none; }
.garden-log-link:hover, .garden-log-link:focus-visible { color: var(--color-accent); }
.garden-log-body p { max-width: 48rem; margin: 0; color: var(--color-text-muted); line-height: 1.65; }
@media (max-width: 640px) {
  .garden-scene { margin-top: 5vh; }
  .garden-heading { align-items: start; flex-direction: column; gap: 1rem; margin-bottom: 3rem; }
  .garden-heading h1 { font-size: clamp(3rem, 18vw, 5rem); }
  .garden-log-entry { grid-template-columns: 1fr; gap: .7rem; padding: 1rem .65rem; }
  .garden-log-meta { display: flex; flex-wrap: wrap; gap: .3rem .75rem; }
}
@media (max-width: 360px) { .garden-scene { margin-top: 2rem; } .garden-log-link { font-size: 1.1rem; } }
@media (prefers-reduced-motion: reduce) { .garden-scene *, .garden-scene *::before, .garden-scene *::after { animation: none !important; transition: none !important; } }
</style>
