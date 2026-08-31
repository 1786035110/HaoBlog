<script setup lang="ts">
import type { GardenGraph, GardenNode } from '../../utils/publicGarden'

const props = defineProps<{ graph: GardenGraph }>()
const emit = defineEmits<{ failed: [] }>()
const host = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const selectedId = ref<string | null>(null)
const status = ref('3D 场景准备中。')
let disposed = false
let visible = false
let pageVisible = true
let raf = 0
let visibilityObserver: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null
let three: typeof import('three') | null = null
let scene: import('three').Scene | null = null
let camera: import('three').PerspectiveCamera | null = null
let renderer: import('three').WebGLRenderer | null = null
let root: import('three').Group | null = null
let nodeMesh: import('three').InstancedMesh | null = null
let lineSegments: import('three').LineSegments | null = null
let nodeGeometry: import('three').BufferGeometry | null = null
let nodeMaterial: import('three').Material | null = null
let lineGeometry: import('three').BufferGeometry | null = null
let lineMaterial: import('three').Material | null = null
let nodePositions = new Map<string, import('three').Vector3>()

const selectedNode = computed(() => props.graph.nodes.find(node => node.id === selectedId.value) || null)

onMounted(() => {
  void mountScene()
})

onBeforeUnmount(() => {
  disposed = true
  stopLoop()
  visibilityObserver?.disconnect()
  visibilityObserver = null
  resizeObserver?.disconnect()
  resizeObserver = null
  document.removeEventListener('visibilitychange', handleVisibility)
  disposeScene()
})

async function mountScene() {
  if (!canvas.value || !host.value) return
  try {
    three = await import('three')
    if (disposed || !three) return
    scene = new three.Scene()
    camera = new three.PerspectiveCamera(42, 1, .1, 100)
    camera.position.z = 7
    renderer = new three.WebGLRenderer({ canvas: canvas.value, antialias: false, alpha: true, powerPreference: 'low-power' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
    renderer.setClearColor(0x000000, 0)
    root = new three.Group()
    scene.add(root)
    buildGraph(three)
    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host.value)
    visibilityObserver = new IntersectionObserver(entries => {
      visible = entries.some(entry => entry.isIntersecting)
      syncLoop()
    }, { threshold: 0 })
    visibilityObserver.observe(host.value)
    document.addEventListener('visibilitychange', handleVisibility)
    resize()
    status.value = '3D 星图已接入。节点清单支持键盘选择，画布节点可点击打开内容。'
  } catch {
    status.value = 'WebGL 或 Three.js 不可用，已回退到上方静态关系图。'
    disposeScene()
    emit('failed')
  }
}

function buildGraph(module: typeof import('three')) {
  if (!root) return
  const positions = props.graph.nodes.map((node, index) => {
    const angle = index * 2.39996
    const radius = 1.25 + (index % 5) * .32
    const position = new module.Vector3(Math.cos(angle) * radius, Math.sin(angle) * radius, ((index % 4) - 1.5) * .32)
    nodePositions.set(node.id, position)
    return position
  })
  nodeGeometry = new module.SphereGeometry(.13, 8, 6)
  nodeMaterial = new module.MeshBasicMaterial({ vertexColors: true })
  nodeMesh = new module.InstancedMesh(nodeGeometry, nodeMaterial, positions.length)
  const matrix = new module.Matrix4()
  props.graph.nodes.forEach((node, index) => {
    const position = positions[index]!
    matrix.makeTranslation(position.x, position.y, position.z)
    nodeMesh?.setMatrixAt(index, matrix)
    nodeMesh?.setColorAt(index, new module.Color(nodeColor(node.type)))
  })
  nodeMesh.instanceMatrix.needsUpdate = true
  nodeMesh.instanceColor && (nodeMesh.instanceColor.needsUpdate = true)
  root.add(nodeMesh)

  const vertices: number[] = []
  props.graph.edges.forEach(edge => {
    const source = nodePositions.get(edge.source)
    const target = nodePositions.get(edge.target)
    if (!source || !target) return
    vertices.push(source.x, source.y, source.z, target.x, target.y, target.z)
  })
  lineGeometry = new module.BufferGeometry()
  lineGeometry.setAttribute('position', new module.Float32BufferAttribute(vertices, 3))
  lineMaterial = new module.LineBasicMaterial({ color: nodeColor('TAG'), transparent: true, opacity: .38 })
  lineSegments = new module.LineSegments(lineGeometry, lineMaterial)
  root.add(lineSegments)
}

function nodeColor(type: GardenNode['type']) {
  return { ARTICLE: 0xe8f1ee, TOOL: 0xff9d3d, TAG: 0x8db7ac, CATEGORY: 0x71d4c2 }[type]
}

function resize() {
  if (!host.value || !camera || !renderer) return
  const rect = host.value.getBoundingClientRect()
  const width = Math.max(280, rect.width)
  const height = Math.max(240, Math.min(520, rect.height))
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, false)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  render()
}

function handleVisibility() {
  pageVisible = document.visibilityState === 'visible'
  syncLoop()
}

function syncLoop() {
  if (visible && pageVisible && !raf && !disposed) raf = requestAnimationFrame(loop)
  if ((!visible || !pageVisible || disposed) && raf) {
    cancelAnimationFrame(raf)
    raf = 0
  }
}

function loop() {
  raf = 0
  if (!visible || !pageVisible || disposed) return
  if (root) root.rotation.y += .0012
  render()
  raf = requestAnimationFrame(loop)
}

function render() {
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function stopLoop() {
  if (raf) cancelAnimationFrame(raf)
  raf = 0
}

function disposeScene() {
  stopLoop()
  nodeGeometry?.dispose()
  nodeMaterial?.dispose()
  lineGeometry?.dispose()
  lineMaterial?.dispose()
  nodeGeometry = null
  nodeMaterial = null
  lineGeometry = null
  lineMaterial = null
  scene?.clear()
  root = null
  nodeMesh = null
  lineSegments = null
  renderer?.forceContextLoss()
  renderer?.dispose()
  renderer = null
  scene = null
  camera = null
  nodePositions.clear()
  if (canvas.value) {
    canvas.value.width = 0
    canvas.value.height = 0
  }
}

function selectNode(node: GardenNode) {
  selectedId.value = node.id
  status.value = `${node.label} 已选择。`
}

function openNode(node: GardenNode | null) {
  if (!node?.href || !/^\/(?!\/)[a-z0-9/?=&_.-]+$/i.test(node.href)) return
  window.location.assign(node.href)
}

function clickCanvas(event: PointerEvent) {
  if (!three || !camera || !nodeMesh || !canvas.value) return
  const rect = canvas.value.getBoundingClientRect()
  const pointer = new three.Vector2(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1)
  const raycaster = new three.Raycaster()
  raycaster.setFromCamera(pointer, camera)
  const hit = raycaster.intersectObject(nodeMesh)[0]
  if (hit?.instanceId == null) return
  const node = props.graph.nodes[hit.instanceId]
  if (!node) return
  selectNode(node)
  openNode(node)
}
</script>

<template>
  <section ref="host" class="home-three-panel" aria-labelledby="home-three-title">
    <div class="home-three-heading">
      <div>
        <p class="instrument-label">THREE / INSTANCED SIGNAL FIELD</p>
        <h3 id="home-three-title">低功耗轨道视图</h3>
      </div>
      <span class="home-three-status" role="status">{{ status }}</span>
    </div>
    <canvas ref="canvas" class="home-three-canvas" aria-label="可点击的公开知识星图 3D 画布" @pointerup="clickCanvas" />
    <p v-if="selectedNode" class="home-three-selected" role="status">已选择：{{ selectedNode.label }}</p>
    <div class="home-three-ledger" role="list" aria-label="3D 星图节点清单">
      <a v-for="node in graph.nodes" :key="node.id" :href="node.href || '/garden'" role="listitem" :aria-current="selectedId === node.id ? 'true' : undefined" @focus="selectNode(node)">
        <span>{{ node.label }}</span><small>{{ node.type }}</small>
      </a>
    </div>
  </section>
</template>

<style scoped>
.home-three-panel { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--color-border); }
.home-three-heading { display: flex; align-items: end; justify-content: space-between; gap: 1rem; }
.home-three-heading h3 { margin: .45rem 0 0; font: 700 clamp(1.5rem, 4vw, 2.5rem)/1 var(--font-display); }
.home-three-status { max-width: 34rem; color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
.home-three-canvas { display: block; width: 100%; height: min(34rem, 60vw); min-height: 18rem; margin-top: 1rem; border: 1px solid var(--color-border); background: var(--color-bg-sub); }
.home-three-selected { margin: .55rem 0 0; color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); }
.home-three-ledger { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; margin-top: .75rem; background: var(--color-border); }
.home-three-ledger a { display: grid; gap: .25rem; min-width: 0; padding: .6rem; color: var(--color-text-main); background: var(--color-bg-base); text-decoration: none; }
.home-three-ledger a:hover, .home-three-ledger a:focus-visible, .home-three-ledger a[aria-current='true'] { color: var(--color-accent); }
.home-three-ledger span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.home-three-ledger small { color: var(--color-text-muted); font: .68rem/1.2 var(--font-mono); }
@media (max-width: 767px) { .home-three-heading { align-items: start; flex-direction: column; }.home-three-canvas { height: 18rem; }.home-three-ledger { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
