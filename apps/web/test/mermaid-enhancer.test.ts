import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

const mermaid = vi.hoisted(() => ({
  initialize: vi.fn(),
  render: vi.fn().mockResolvedValue({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><text>graph</text></svg>' }),
}))

vi.mock('mermaid', () => ({ default: mermaid }))

import MermaidEnhancer from '../app/components/articles/MermaidEnhancer.vue'
import { MERMAID_CONFIG } from '../app/utils/mermaidConfig'

type FakeEntry = { isIntersecting: boolean; target: Element }

class FakeIntersectionObserver {
  static instances: FakeIntersectionObserver[] = []
  readonly callback: (entries: FakeEntry[]) => void
  readonly observe = vi.fn()
  readonly unobserve = vi.fn()
  readonly disconnect = vi.fn()

  constructor(callback: (entries: FakeEntry[]) => void) {
    this.callback = callback
    FakeIntersectionObserver.instances.push(this)
  }
}

function setBrowserPreferences({ saveData = false, reducedMotion = false } = {}) {
  Object.defineProperty(navigator, 'connection', {
    configurable: true,
    value: { saveData, addEventListener: vi.fn(), removeEventListener: vi.fn() },
  })
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockReturnValue({ matches: reducedMotion, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  })
  Object.defineProperty(window, 'IntersectionObserver', { configurable: true, value: FakeIntersectionObserver })
  Object.defineProperty(globalThis, 'IntersectionObserver', { configurable: true, value: FakeIntersectionObserver })
}

function mountEnhancer(source = 'flowchart TD\nA-->B') {
  const host = defineComponent({
    setup: () => () => h('div', { class: 'safe-markdown' }, [
      h('figure', { class: 'mermaid-figure', 'data-mermaid-figure': 'true', 'data-mermaid-state': 'source' }, [
        h('figcaption', [h('button', { type: 'button', 'data-mermaid-render': 'true' }, '渲染图表'), h('span', { 'data-mermaid-status': 'true' })]),
        h('div', { 'data-mermaid-output': 'true', hidden: true }),
        h('pre', { 'data-mermaid-source': 'true' }, [h('code', source)]),
      ]),
      h(MermaidEnhancer),
    ]),
  })
  const wrapper = mount(host, { attachTo: document.body })
  return { wrapper, figure: wrapper.find<HTMLElement>('[data-mermaid-figure]').element }
}

describe('MermaidEnhancer', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    FakeIntersectionObserver.instances = []
    mermaid.initialize.mockClear()
    mermaid.render.mockReset().mockResolvedValue({ svg: '<svg xmlns="http://www.w3.org/2000/svg"><text>graph</text></svg>' })
    setBrowserPreferences()
  })

  it('loads Mermaid only when a figure intersects and locks the security config', async () => {
    const source = '%%{init: {"securityLevel":"loose","htmlLabels":true}}%%\nflowchart TD\nA-->B'
    const { wrapper, figure } = mountEnhancer(source)
    await flushPromises()
    expect(mermaid.render).not.toHaveBeenCalled()
    expect(FakeIntersectionObserver.instances[0]!.observe).toHaveBeenCalledWith(figure)

    FakeIntersectionObserver.instances[0]!.callback([{ isIntersecting: true, target: figure }])
    await flushPromises()

    expect(mermaid.initialize).toHaveBeenCalledWith(expect.objectContaining(MERMAID_CONFIG))
    expect(mermaid.initialize.mock.calls[0]![0]).toMatchObject({
      startOnLoad: false,
      securityLevel: 'strict',
      maxTextSize: 50_000,
      maxEdges: 500,
      htmlLabels: false,
      secure: expect.arrayContaining(['securityLevel', 'startOnLoad', 'maxTextSize', 'maxEdges', 'htmlLabels']),
    })
    expect(mermaid.render).toHaveBeenCalledWith(expect.stringMatching(/^haoblog-mermaid-/), source)
    expect(figure.querySelector('[data-mermaid-output]')?.innerHTML).toContain('<svg')
    expect(figure.querySelector('[data-mermaid-source]')?.hasAttribute('hidden')).toBe(true)
    wrapper.unmount()
  })

  it('does not auto-load with Save-Data and renders after a button click', async () => {
    setBrowserPreferences({ saveData: true })
    const { wrapper, figure } = mountEnhancer()
    await flushPromises()
    expect(FakeIntersectionObserver.instances).toHaveLength(0)
    const button = figure.querySelector<HTMLButtonElement>('[data-mermaid-render]')!
    expect(button.hidden).toBe(false)
    button.click()
    await flushPromises()
    expect(mermaid.render).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('marks reduced-motion figures and keeps source after invalid or oversized charts', async () => {
    setBrowserPreferences({ reducedMotion: true })
    const reduced = mountEnhancer()
    await flushPromises()
    expect(FakeIntersectionObserver.instances).toHaveLength(0)
    expect(reduced.figure.classList.contains('mermaid-reduced-motion')).toBe(true)
    expect(reduced.figure.querySelector('[data-mermaid-render]')?.hidden).toBe(false)
    expect(mermaid.render).not.toHaveBeenCalled()
    reduced.wrapper.unmount()

    setBrowserPreferences({ reducedMotion: false })
    mermaid.render.mockRejectedValueOnce(new Error('invalid chart'))
    const invalid = mountEnhancer()
    await flushPromises()
    FakeIntersectionObserver.instances[0]!.callback([{ isIntersecting: true, target: invalid.figure }])
    await flushPromises()
    expect(invalid.figure.classList.contains('mermaid-reduced-motion')).toBe(false)
    expect(invalid.figure.dataset.mermaidState).toBe('error')
    expect(invalid.figure.querySelector('[data-mermaid-source]')?.textContent).toContain('A-->B')
    expect(invalid.figure.querySelector('[data-mermaid-status]')?.textContent).toContain('保留源码')
    invalid.wrapper.unmount()

    const oversized = mountEnhancer('x'.repeat(50_001))
    await flushPromises()
    FakeIntersectionObserver.instances[1]!.callback([{ isIntersecting: true, target: oversized.figure }])
    await flushPromises()
    expect(mermaid.render).toHaveBeenCalledTimes(1)
    expect(oversized.figure.dataset.mermaidState).toBe('error')
    expect(oversized.figure.querySelector('[data-mermaid-source]')?.textContent).toHaveLength(50_001)
    oversized.wrapper.unmount()

    mermaid.render.mockRejectedValueOnce(new Error('maxEdges'))
    const overEdges = mountEnhancer(Array.from({ length: 501 }, (_, index) => `N${index}-->N${index + 1}`).join('\n'))
    await flushPromises()
    FakeIntersectionObserver.instances[2]!.callback([{ isIntersecting: true, target: overEdges.figure }])
    await flushPromises()
    expect(overEdges.figure.dataset.mermaidState).toBe('error')
    expect(overEdges.figure.querySelector('[data-mermaid-source]')?.textContent).toContain('N500-->N501')
    overEdges.wrapper.unmount()
  })

  it('cleans observers, listeners, and generated SVG when unmounted', async () => {
    let resolveRender!: (value: { svg: string }) => void
    mermaid.render.mockReturnValueOnce(new Promise(resolve => { resolveRender = resolve }))
    const { wrapper, figure } = mountEnhancer()
    await flushPromises()
    const observer = FakeIntersectionObserver.instances[0]!
    observer.callback([{ isIntersecting: true, target: figure }])
    wrapper.unmount()
    resolveRender({ svg: '<svg><text>late</text></svg>' })
    await flushPromises()
    expect(observer.disconnect).toHaveBeenCalled()
    expect(figure.querySelector('[data-mermaid-output]')?.children).toHaveLength(0)
    expect(figure.querySelector('[data-mermaid-source]')?.hasAttribute('hidden')).toBe(false)
  })
})
