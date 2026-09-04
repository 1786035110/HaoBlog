import { createSSRApp, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { renderToString } from 'vue/server-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { components } from '@haoblog/api-client'
import CommentSignalSection from '../app/components/articles/CommentSignalSection.vue'
import { splitCommentSignalText } from '../app/utils/commentSignal'

type CommentPage = components['schemas']['CommentPageResponse']

const comments: CommentPage = {
  items: [{
    id: '00000000-0000-7000-8000-000000000001',
    nickname: '观测员',
    content: '纯文本 <script>alert(1)</script> https://example.com/echo.',
    createdAt: '2026-08-20T03:00:00Z',
    replies: [{
      id: '00000000-0000-7000-8000-000000000002',
      nickname: '回声',
      content: 'reply',
      createdAt: '2026-08-20T04:00:00Z',
      replies: [],
    }],
  }],
  page: 0,
  size: 20,
  total: 1,
}

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('comment signal', () => {
  it('splits only valid https URLs and preserves punctuation as text', () => {
    expect(splitCommentSignalText('看 https://example.com/a。 和 javascript://bad')).toEqual([
      { text: '看 ' },
      { text: 'https://example.com/a', href: 'https://example.com/a' },
      { text: '。 和 javascript://bad' },
    ])
  })

  it('renders SSR comments as readable text and safe links without raw HTML', async () => {
    const html = await renderToString(createSSRApp(CommentSignalSection, {
      slug: 'signal', comments, siteCommentsEnabled: true, articleCommentsEnabled: true,
    }))
    expect(html).toContain('纯文本 &lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).toContain('href="https://example.com/echo"')
    expect(html).toContain('回声')
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('v-html')
    expect(html).not.toContain('comment-signal-form')
  })

  it('requests form context only after the visitor expands the form and sends CSRF on submit', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-22T00:00:00Z'))
    const fetcher = vi.fn()
      .mockResolvedValueOnce({ csrfToken: 'csrf-1', challenge: 'challenge-1', expiresAt: '2026-08-22T00:10:00Z', commentsEnabled: true })
      .mockResolvedValueOnce({ id: comments.items[0]!.id, status: 'APPROVED', createdAt: '2026-08-22T00:00:04Z', deleteToken: 'delete-1' })
    vi.stubGlobal('$fetch', fetcher)
    const wrapper = mount(CommentSignalSection, {
      props: { slug: 'signal', comments, siteCommentsEnabled: true, articleCommentsEnabled: true },
    })

    expect(fetcher).not.toHaveBeenCalled()
    await wrapper.get('.comment-signal-expand').trigger('click')
    await nextTick()
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher.mock.calls[0]?.[0]).toContain('/comments/form-context')

    await wrapper.get('input[autocomplete="nickname"]').setValue('访客')
    await wrapper.get('textarea').setValue('留下一个回波')
    vi.advanceTimersByTime(3000)
    await wrapper.get('form').trigger('submit')
    await nextTick()
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      headers: { 'X-CSRF-TOKEN': 'csrf-1' },
    })
    expect(wrapper.get('[data-status="published"]').text()).toContain('已发布')
    expect(localStorage.getItem(`haoblog-comment-delete:${comments.items[0]!.id}`)).toBe('delete-1')
    vi.useRealTimers()
  })

  it('restores a per-comment delete token from localStorage and sends it with CSRF', async () => {
    const id = comments.items[0]!.id
    localStorage.setItem(`haoblog-comment-delete:${id}`, 'delete-restored')
    vi.stubGlobal('confirm', vi.fn(() => true))
    const fetcher = vi.fn()
      .mockResolvedValueOnce({ csrfToken: 'csrf-delete', challenge: 'unused', expiresAt: '2030-01-01T00:00:00Z', commentsEnabled: true })
      .mockResolvedValueOnce(undefined)
    vi.stubGlobal('$fetch', fetcher)
    const wrapper = mount(CommentSignalSection, {
      props: { slug: 'signal', comments, siteCommentsEnabled: true, articleCommentsEnabled: true },
    })
    await nextTick()
    const deleteButton = wrapper.findAll('button').find(button => button.text() === '删除我的评论')
    expect(deleteButton).toBeDefined()
    await deleteButton!.trigger('click')
    await flushPromises()
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
      method: 'DELETE',
      headers: { 'X-CSRF-TOKEN': 'csrf-delete', 'X-Comment-Delete-Token': 'delete-restored' },
    })
    expect(localStorage.getItem(`haoblog-comment-delete:${id}`)).toBeNull()
    expect(wrapper.text()).not.toContain('纯文本')
  })

  it('surfaces Retry-After for rate-limited submissions', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-22T00:00:00Z'))
    const fetcher = vi.fn()
      .mockResolvedValueOnce({ csrfToken: 'csrf-429', challenge: 'challenge-429', expiresAt: '2026-08-22T00:10:00Z', commentsEnabled: true })
      .mockRejectedValueOnce({ response: { status: 429, headers: new Headers({ 'Retry-After': '17' }), _data: { detail: 'rate limited' } } })
    vi.stubGlobal('$fetch', fetcher)
    const wrapper = mount(CommentSignalSection, {
      props: { slug: 'signal', comments, siteCommentsEnabled: true, articleCommentsEnabled: true },
    })
    await wrapper.get('.comment-signal-expand').trigger('click')
    await wrapper.get('textarea').setValue('稍后再试')
    vi.advanceTimersByTime(3000)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-status="rate-limited"]').text()).toContain('17 秒')
    vi.useRealTimers()
  })

  it('keeps history visible for a single article close and shows the global close signal', async () => {
    const singleClosed = await renderToString(createSSRApp(CommentSignalSection, {
      slug: 'signal', comments, siteCommentsEnabled: true, articleCommentsEnabled: false,
    }))
    expect(singleClosed).toContain('历史回波仍可读取')
    expect(singleClosed).toContain('纯文本')
    expect(singleClosed).not.toContain('展开评论入口')

    const globalClosed = await renderToString(createSSRApp(CommentSignalSection, {
      slug: 'signal', comments: { ...comments, items: [], total: 0 }, siteCommentsEnabled: false, articleCommentsEnabled: true,
    }))
    expect(globalClosed).toContain('评论信号已关闭')
  })
})
