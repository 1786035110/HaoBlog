<script setup lang="ts">
import type { Simulation, SimulationLinkDatum, SimulationNodeDatum } from 'd3-force'
import type { GardenEdge, GardenGraph, GardenNode } from '~/utils/publicGarden'
import { gardenNodeTypeLabel } from '~/utils/publicGarden'

type RenderNode = GardenNode & SimulationNodeDatum & { x: number; y: number; fx?: number | null; fy?: number | null }
type RenderLink = GardenEdge & SimulationLinkDatum<RenderNode>

const props = defineProps<{ graph: GardenGraph }>()
const canvas = ref<HTMLCanvasElement | null>(null)
const host = ref<HTMLElement | null>(null)
const selectedId = ref<string | null>(null)
const status = ref('桌面星图正在校准。')
const view = reactive({ scale: 1, x: 0, y: 0 })

let context: CanvasRenderingContext2D | null = null
let simulation: Simulation<RenderNode, RenderLink> | null = null
let resizeObserver: ResizeObserver | null = null
let frame = 0
let disposed = false
let dragging: RenderNode | null = null
let dragPointerId: number | null = null
let renderNodes: RenderNode[] = []
let renderLinks: RenderLink[] = []

const selectedNode = computed(() => props.graph.nodes.find(node => node.id === selectedId.value) || null)

onMounted(async () => {
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const coarsePointer = window.matchMedia('(pointer: coarse)').matches
  if (window.innerWidth < 768 || connection?.saveData || reducedMotion || coarsePointer) {
    status.value = '当前设备保留 SSR 时间线，不加载动态星图。'
    return
  }
  context = canvas.value?.getContext('2d') || null
  if (!context || !canvas.value) {
    status.value = 'Canvas 不可用，已保留下方文字时间线。'
    return
  }
  try {
    const { forceCenter, forceCollide, forceLink, forceManyBody, forceSimulation } = await import('d3-force')
    if (disposed) return
    renderNodes = props.graph.nodes.map((node, index) => ({
      ...node,
      x: 80 + (index % 8) * 110,
      y: 70 + Math.floor(index / 8) * 80,
    }))
    renderLinks = props.graph.edges.map(edge => ({ ...edge })) as RenderLink[]
    const size = measure()
    simulation = forceSimulation<RenderNode>(renderNodes)
      .force('link', forceLink<RenderNode, RenderLink>(renderLinks).id(node => node.id).distance(115).strength(.5))
      .force('charge', forceManyBody<RenderNode>().strength(-190).distanceMax(500))
      .force('center', forceCenter<RenderNode>(size.width / 2, size.height / 2))
      .force('collide', forceCollide<RenderNode>().radius(nodeRadius).strength(.8))
      .on('tick', scheduleDraw)
    resizeObserver = new ResizeObserver(() => resize())
    if (host.value) resizeObserver.observe(host.value)
    status.value = '星图已接入。可使用节点清单、拖拽、滚轮缩放或重置视图。'
    resize()
    scheduleDraw()
  } catch {
    status.value = '动态星图加载失败，已保留下方文字时间线。'
  }
})

onBeforeUnmount(() => {
  disposed = true
  simulation?.stop()
  simulation?.on('tick', null)
  simulation = null
  resizeObserver?.disconnect()
  resizeObserver = null
  if (frame) cancelAnimationFrame(frame)
  frame = 0
  context = null
})

function measure() {
  const rect = host.value?.getBoundingClientRect()
  return { width: Math.max(320, rect?.width || 720), height: Math.max(320, Math.min(620, rect?.height || 480)) }
}

function resize() {
  if (!canvas.value || !context) return
  const { width, height } = measure()
  const dpr = Math.min(window.devicePixelRatio || 1, 1.5)
  canvas.value.width = Math.floor(width * dpr)
  canvas.value.height = Math.floor(height * dpr)
  canvas.value.style.width = `${width}px`
  canvas.value.style.height = `${height}px`
  context.setTransform(dpr, 0, 0, dpr, 0, 0)
  const center = simulation?.force('center') as { x?: (value: number) => unknown; y?: (value: number) => unknown } | undefined
  center?.x?.(width / 2)
  center?.y?.(height / 2)
  scheduleDraw()
}

function scheduleDraw() {
  if (frame || disposed) return
  frame = requestAnimationFrame(() => {
    frame = 0
    draw()
  })
}

function draw() {
  if (!context || !canvas.value) return
  const { width, height } = measure()
  context.clearRect(0, 0, width, height)
  const styles = getComputedStyle(canvas.value)
  const accent = styles.getPropertyValue('--garden-accent') || '#a8ff60'
  const line = styles.getPropertyValue('--garden-line') || 'rgba(168,255,96,.2)'
  for (const edge of renderLinks) {
    const source = edge.source as RenderNode
    const target = edge.target as RenderNode
    if (!source || !target || source.x == null || target.x == null) continue
    const left = screenPoint(source)
    const right = screenPoint(target)
    context.beginPath()
    context.moveTo(left.x, left.y)
    context.lineTo(right.x, right.y)
    context.lineWidth = Math.min(4, 1 + edge.weight * .45)
    context.strokeStyle = line
    context.stroke()
  }
  for (const node of renderNodes) {
    if (node.x == null || node.y == null) continue
    const point = screenPoint(node)
    const radius = nodeRadius(node)
    context.beginPath()
    context.arc(point.x, point.y, radius, 0, Math.PI * 2)
    context.fillStyle = node.id === selectedId.value ? accent : nodeColor(node.type)
    context.fill()
    context.lineWidth = node.id === selectedId.value ? 2 : 1
    context.strokeStyle = accent
    context.stroke()
  }
}

function screenPoint(node: RenderNode) {
  return { x: (node.x * view.scale) + view.x, y: (node.y * view.scale) + view.y }
}

function nodeRadius(node: RenderNode) {
  return Math.min(11, 4 + Math.sqrt(Math.max(0, node.degree)) * 1.7)
}

function nodeColor(type: GardenNode['type']) {
  return { ARTICLE: '#e8f1ee', TOOL: '#ff9d3d', TAG: '#8db7ac', CATEGORY: '#71d4c2' }[type]
}

function hitNode(event: PointerEvent | MouseEvent) {
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return null
  const x = (event.clientX - rect.left - view.x) / view.scale
  const y = (event.clientY - rect.top - view.y) / view.scale
  return [...renderNodes].reverse().find(node => Math.hypot(node.x - x, node.y - y) <= nodeRadius(node) + 8) || null
}

function selectNode(node: GardenNode | RenderNode) {
  selectedId.value = node.id
  status.value = `${gardenNodeTypeLabel(node.type)}：${node.label} 已选择。`
  scheduleDraw()
}

function focusNode(node: GardenNode) {
  selectNode(node)
  const renderNode = renderNodes.find(item => item.id === node.id)
  if (!renderNode) return
  const { width, height } = measure()
  view.x = width / 2 - renderNode.x * view.scale
  view.y = height / 2 - renderNode.y * view.scale
  scheduleDraw()
}

function openSelected() {
  if (selectedNode.value?.href) window.location.assign(selectedNode.value.href)
}

function resetView() {
  view.scale = 1
  view.x = 0
  view.y = 0
  simulation?.alpha(.2).restart()
  scheduleDraw()
}

function zoom(event: WheelEvent) {
  event.preventDefault()
  view.scale = Math.max(.55, Math.min(2.5, view.scale * (event.deltaY < 0 ? 1.1 : .9)))
  scheduleDraw()
}

function pointerDown(event: PointerEvent) {
  const node = hitNode(event)
  if (!node) return
  dragging = node
  dragPointerId = event.pointerId
  canvas.value?.setPointerCapture(event.pointerId)
  selectNode(node)
  simulation?.alphaTarget(.25).restart()
  node.fx = node.x
  node.fy = node.y
}

function pointerMove(event: PointerEvent) {
  if (!dragging || dragPointerId !== event.pointerId) return
  const rect = canvas.value?.getBoundingClientRect()
  if (!rect) return
  dragging.fx = (event.clientX - rect.left - view.x) / view.scale
  dragging.fy = (event.clientY - rect.top - view.y) / view.scale
  scheduleDraw()
}

function pointerUp(event: PointerEvent) {
  if (!dragging || dragPointerId !== event.pointerId) return
  dragging.fx = null
  dragging.fy = null
  simulation?.alphaTarget(0)
  dragging = null
  dragPointerId = null
}

function clickCanvas(event: MouseEvent) {
  const node = hitNode(event)
  if (node) selectNode(node)
}

function doubleClickCanvas(event: MouseEvent) {
  const node = hitNode(event)
  if (node?.href) window.location.assign(node.href)
}
</script>

<template>
  <section class="garden-canvas-panel" aria-labelledby="garden-canvas-title">
    <div class="garden-canvas-header">
      <div>
        <p class="instrument-label">DESKTOP FORCE FIELD / CANVAS</p>
        <h2 id="garden-canvas-title">星图接入</h2>
      </div>
      <button class="garden-reset" type="button" @click="resetView">RESET VIEW</button>
    </div>
    <div ref="host" class="garden-canvas-wrap">
      <canvas
        ref="canvas"
        tabindex="0"
        role="img"
        aria-label="知识星图 Canvas；使用下方节点清单进行键盘选择"
        @click="clickCanvas"
        @dblclick="doubleClickCanvas"
        @pointerdown="pointerDown"
        @pointermove="pointerMove"
        @pointerup="pointerUp"
        @pointercancel="pointerUp"
        @wheel.prevent="zoom"
        @keydown.enter="openSelected"
      />
      <p class="garden-canvas-status" role="status" aria-live="polite">{{ status }}</p>
    </div>
    <div class="garden-legend" aria-label="节点类型图例">
      <span v-for="type in ['ARTICLE', 'TOOL', 'TAG', 'CATEGORY']" :key="type"><i :class="`legend-dot legend-dot--${type.toLowerCase()}`" aria-hidden="true" /> {{ gardenNodeTypeLabel(type as GardenNode['type']) }}</span>
    </div>
    <div class="garden-node-ledger" role="list" aria-label="星图节点键盘清单">
      <button v-for="node in graph.nodes" :key="node.id" type="button" role="listitem" class="garden-node-button" :aria-pressed="selectedId === node.id" @focus="focusNode(node)" @click="focusNode(node)">
        <span class="node-type">{{ gardenNodeTypeLabel(node.type) }}</span>
        <span>{{ node.label }}</span>
        <span class="node-degree">{{ node.degree }}</span>
      </button>
    </div>
    <aside v-if="selectedNode" class="garden-preview" aria-live="polite" aria-labelledby="garden-preview-title">
      <p class="instrument-label">NODE PREVIEW</p>
      <h3 id="garden-preview-title">{{ selectedNode.label }}</h3>
      <p>{{ selectedNode.summary || '这是一个公开知识节点。' }}</p>
      <a :href="selectedNode.href" class="garden-open-link">打开内容 <span aria-hidden="true">→</span></a>
    </aside>
  </section>
</template>

<style scoped>
.garden-canvas-panel { --garden-accent: #a8ff60; --garden-line: rgba(168,255,96,.2); margin: 3rem 0 0; padding: 1rem 0 0; border-top: 1px solid var(--color-border); }
.garden-canvas-header { display: flex; align-items: end; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
.garden-canvas-header h2 { margin: .45rem 0 0; font: 700 clamp(1.8rem, 4vw, 3.5rem)/1 var(--font-display); letter-spacing: -.05em; }
.garden-reset, .garden-node-button, .garden-open-link { border: 1px solid var(--color-border); background: transparent; color: var(--color-text-muted); font: var(--text-xs)/1.2 var(--font-mono); letter-spacing: .04em; }
.garden-reset { padding: .55rem .7rem; cursor: pointer; }
.garden-reset:hover, .garden-reset:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.garden-canvas-wrap { position: relative; min-height: 25rem; overflow: hidden; border: 1px solid var(--color-border); background: radial-gradient(circle at 50% 50%, color-mix(in srgb, var(--color-accent) 5%, transparent), transparent 58%), var(--color-bg-sub); background-image: linear-gradient(var(--color-grid) 1px, transparent 1px), linear-gradient(90deg, var(--color-grid) 1px, transparent 1px); background-size: 48px 48px; }
.garden-canvas-wrap canvas { display: block; width: 100%; height: 30rem; cursor: grab; }
.garden-canvas-wrap canvas:active { cursor: grabbing; }
.garden-canvas-status { position: absolute; left: .75rem; bottom: .6rem; margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1.35 var(--font-mono); }
.garden-legend { display: flex; flex-wrap: wrap; gap: .55rem 1rem; margin: .8rem 0; color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.legend-dot { display: inline-block; width: .55rem; height: .55rem; margin-right: .25rem; border-radius: 50%; background: var(--color-text-main); }
.legend-dot--tool { background: var(--color-warn); } .legend-dot--tag { background: var(--color-text-muted); } .legend-dot--category { background: #71d4c2; }
.garden-node-ledger { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; max-height: 14rem; overflow: auto; background: var(--color-border); }
.garden-node-button { display: grid; grid-template-columns: 1fr auto; gap: .25rem .5rem; padding: .65rem; text-align: left; cursor: pointer; background: var(--color-bg-base); }
.garden-node-button:hover, .garden-node-button:focus-visible, .garden-node-button[aria-pressed='true'] { border-color: var(--color-accent); color: var(--color-text-main); }
.garden-node-button .node-type { grid-column: 1 / -1; overflow: hidden; color: var(--color-accent); text-overflow: ellipsis; white-space: nowrap; }
.node-degree { color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
.garden-preview { display: grid; gap: .55rem; max-width: 40rem; margin-top: 1rem; padding: 1rem; border-left: 2px solid var(--color-accent); background: color-mix(in srgb, var(--color-accent) 6%, transparent); }
.garden-preview h3 { margin: 0; font: 700 1.35rem/1.2 var(--font-display); }
.garden-preview p:not(.instrument-label) { margin: 0; color: var(--color-text-muted); line-height: 1.6; }
.garden-open-link { width: fit-content; padding: .55rem .7rem; border-color: var(--color-accent); color: var(--color-accent); text-decoration: none; }
.garden-open-link:hover, .garden-open-link:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
@media (max-width: 900px) { .garden-node-ledger { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .garden-canvas-header { align-items: start; flex-direction: column; } .garden-canvas-wrap { min-height: 20rem; } .garden-canvas-wrap canvas { height: 22rem; } }
@media (prefers-reduced-motion: reduce) { .garden-canvas-panel *, .garden-canvas-panel *::before, .garden-canvas-panel *::after { animation: none !important; transition: none !important; } }
</style>
