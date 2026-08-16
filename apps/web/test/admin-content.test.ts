import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminContent } from '../app/composables/useAdminContent'
import { useAdminSession } from '../app/composables/useAdminSession'

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: vi.fn().mockResolvedValue(body) }
}

describe('admin content client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    const session = useAdminSession()
    session.clear()
    session.initialized.value = true
  })

  it('loads filtered article pages and preserves category ids', async () => {
    fetchMock.mockResolvedValue(response({ items: [{ id: 'a1', title: 'Signal', slug: 'signal', status: 'DRAFT', categoryId: 'c1', updatedAt: '2030-01-01T00:00:00Z', version: 0 }], page: 0, size: 20, total: 1 }))
    const result = await useAdminContent().listArticles({ keyword: 'signal path', status: 'DRAFT' })
    expect(result.items[0]?.categoryId).toBe('c1')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/articles?page=0&size=20&direction=desc&status=DRAFT&keyword=signal+path')
  })

  it('sends create writes only through the CSRF-aware path', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-1' }))
      .mockResolvedValueOnce(response({ id: 'a1', title: 'Title', markdown: '', status: 'DRAFT', tagIds: [], version: 0 }))
    await useAdminContent().createArticle({ title: 'Title', markdown: '', scheduledAt: null, categoryId: null, coverMediaId: null, tagIds: [] })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/articles')
    expect(fetchMock.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-1')
  })

  it('loads version summaries and restores through the CSRF-aware path', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ items: [{ id: 'r1', sourceArticleVersion: 2, createdAt: '2030-01-01T00:00:00Z', currentPublished: true }], page: 0, size: 20, total: 1 }))
      .mockResolvedValueOnce(response({ token: 'csrf-2' }))
      .mockResolvedValueOnce(response({ id: 'a1', title: 'Restored', markdown: '# old', status: 'PUBLISHED', tagIds: [], version: 3 }))
    const content = useAdminContent()
    await expect(content.listArticleVersions('a1')).resolves.toMatchObject({ total: 1 })
    await content.restoreArticleVersion('a1', 'r1', 2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/articles/a1/versions?page=0&size=20')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/admin/articles/a1/versions/r1/restore')
    expect(fetchMock.mock.calls[2][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-2')
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ version: 2 })
  })
})
