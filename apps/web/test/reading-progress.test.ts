import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import ReadingProgress from '../app/components/ReadingProgress.vue'
import { useScrollProgress } from '../app/composables/useScrollProgress'

describe('shared reading progress listener', () => {
  it('shares one scroll listener and removes it after the last subscriber unmounts', async () => {
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    const ArticleSignal = defineComponent({
      setup() {
        const { articleProgress } = useScrollProgress()
        return () => h('span', String(articleProgress.value))
      },
    })
    const progress = mount(ReadingProgress)
    const article = mount(ArticleSignal)
    await nextTick()
    expect(add.mock.calls.filter(([type]) => type === 'scroll')).toHaveLength(1)

    progress.unmount()
    expect(remove.mock.calls.filter(([type]) => type === 'scroll')).toHaveLength(0)
    article.unmount()
    expect(remove.mock.calls.filter(([type]) => type === 'scroll')).toHaveLength(1)
    add.mockRestore()
    remove.mockRestore()
  })
})
