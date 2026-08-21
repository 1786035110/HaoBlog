<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDataSaver } from '../../composables/useDataSaver'
import { useMotionPreference } from '../../composables/useMotionPreference'
import { MERMAID_CONFIG } from '../../utils/mermaidConfig'

type MermaidApi = {
  initialize: (config: Record<string, unknown>) => void
  render: (id: string, source: string) => Promise<{ svg: string }>
}

const MAX_TEXT_SIZE = 50_000
const marker = ref<HTMLElement | null>(null)
const { enabled: saveData } = useDataSaver()
const { reduced } = useMotionPreference()

let observer: IntersectionObserver | undefined
let figures: HTMLElement[] = []
let mermaidPromise: Promise<MermaidApi> | undefined
let destroyed = false
let renderSequence = 0
const clickHandlers = new Map<HTMLButtonElement, () => void>()

function setStatus(figure: HTMLElement, state: 'source' | 'loading' | 'ready' | 'error', message = '') {
  figure.dataset.mermaidState = state
  const status = figure.querySelector<HTMLElement>('[data-mermaid-status]')
  if (status) status.textContent = message
}

function setManualButton(figure: HTMLElement, visible: boolean, label = '渲染图表') {
  const button = figure.querySelector<HTMLButtonElement>('[data-mermaid-render]')
  if (!button) return
  button.hidden = !visible
  button.textContent = label
}

function loadMermaid() {
  mermaidPromise ??= import('mermaid').then(module => {
    const mermaid = (module.default || module) as unknown as MermaidApi
    mermaid.initialize({ ...MERMAID_CONFIG, secure: [...MERMAID_CONFIG.secure] })
    return mermaid
  })
  return mermaidPromise
}

async function renderFigure(figure: HTMLElement) {
  if (destroyed || figure.dataset.mermaidState === 'loading' || figure.dataset.mermaidState === 'ready') return
  const source = figure.querySelector<HTMLElement>('[data-mermaid-source] code')?.textContent || ''
  const sourceBlock = figure.querySelector<HTMLElement>('[data-mermaid-source]')
  const output = figure.querySelector<HTMLElement>('[data-mermaid-output]')
  if (!output || !sourceBlock) return

  setStatus(figure, 'loading', '正在解析图表…')
  setManualButton(figure, false)
  try {
    if (source.length > MAX_TEXT_SIZE) throw new Error('Mermaid 源码超过安全限制')
    const mermaid = await loadMermaid()
    const { svg } = await mermaid.render(`haoblog-mermaid-${++renderSequence}`, source)
    if (destroyed) return
    const template = document.createElement('template')
    template.innerHTML = svg
    const rendered = template.content.firstElementChild
    if (!rendered || rendered.tagName.toLowerCase() !== 'svg') throw new Error('Mermaid 输出无效')
    rendered.querySelectorAll('script,iframe,object,embed').forEach(element => element.remove())
    rendered.querySelectorAll('*').forEach(element => {
      Array.from(element.attributes).forEach(attribute => {
        if (/^on/i.test(attribute.name)) element.removeAttribute(attribute.name)
      })
    })
    output.replaceChildren(rendered)
    output.hidden = false
    sourceBlock.hidden = true
    setStatus(figure, 'ready', '图表已渲染')
  } catch {
    if (destroyed) return
    output.replaceChildren()
    output.hidden = true
    sourceBlock.hidden = false
    setStatus(figure, 'error', '图表解析失败，已保留源码。')
    setManualButton(figure, true, '重试渲染')
  }
}

function observeFigures() {
  if (destroyed || saveData.value) return
  if (!('IntersectionObserver' in window)) {
    figures.forEach(figure => setManualButton(figure, true))
    return
  }
  observer?.disconnect()
  observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        observer?.unobserve(entry.target)
        void renderFigure(entry.target as HTMLElement)
      }
    })
  }, { rootMargin: '0px', threshold: 0.1 })
  figures.forEach(figure => {
    setManualButton(figure, false)
    observer?.observe(figure)
  })
}

function updateReducedMotion() {
  figures.forEach(figure => figure.classList.toggle('mermaid-reduced-motion', reduced.value))
}

onMounted(async () => {
  await nextTick()
  figures = Array.from(marker.value?.parentElement?.querySelectorAll<HTMLElement>('[data-mermaid-figure]') || [])
  figures.forEach(figure => {
    const button = figure.querySelector<HTMLButtonElement>('[data-mermaid-render]')
    if (button) {
      const handler = () => void renderFigure(figure)
      clickHandlers.set(button, handler)
      button.addEventListener('click', handler)
    }
  })
  updateReducedMotion()
  if (saveData.value) figures.forEach(figure => setManualButton(figure, true))
  else observeFigures()
})

watch(saveData, enabled => {
  if (enabled) {
    observer?.disconnect()
    figures.forEach(figure => setManualButton(figure, true))
  } else {
    observeFigures()
  }
})

watch(reduced, updateReducedMotion)

onBeforeUnmount(() => {
  destroyed = true
  observer?.disconnect()
  clickHandlers.forEach((handler, button) => button.removeEventListener('click', handler))
  clickHandlers.clear()
  figures.forEach(figure => {
    figure.querySelector<HTMLElement>('[data-mermaid-output]')?.replaceChildren()
    const source = figure.querySelector<HTMLElement>('[data-mermaid-source]')
    if (source) source.hidden = false
  })
  figures = []
})
</script>

<template>
  <span ref="marker" class="mermaid-enhancer-marker" hidden aria-hidden="true" />
</template>
