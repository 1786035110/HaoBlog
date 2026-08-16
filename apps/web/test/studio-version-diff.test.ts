import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StudioVersionDiff from '../app/components/studio/StudioVersionDiff.vue'

function version(id: string, markdown: string) {
  return {
    id, articleId: 'a1', sourceArticleVersion: id === 'v1' ? 1 : 2, title: id, slug: id,
    excerpt: null, markdown, seoTitle: null, seoDescription: null, coverMediaId: null,
    categorySnapshot: null, tagSnapshot: [], changeReason: null, createdBy: null, createdAt: '2030-01-01T00:00:00Z',
  }
}

describe('Studio version diff', () => {
  it('renders explicit added, removed and unchanged row states', async () => {
    const wrapper = mount(StudioVersionDiff, { props: { left: version('v1', 'same\nremoved\n') as never, right: version('v2', 'same\nadded\n') as never } })
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(wrapper.findAll('li[data-kind="unchanged"]')).toHaveLength(1)
    expect(wrapper.findAll('li[data-kind="removed"]')).toHaveLength(1)
    expect(wrapper.findAll('li[data-kind="added"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('新增')
    expect(wrapper.text()).toContain('删除')
    expect(wrapper.text()).toContain('未变')
  })
})
