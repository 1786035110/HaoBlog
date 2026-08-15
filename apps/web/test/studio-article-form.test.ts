import { describe, expect, it } from 'vitest'
import { articleFormSnapshot, articleToForm, formToCreateRequest, formToUpdateRequest, shouldConfirmArticleLeave, validateArticleForm, type ArticleFormModel } from '../app/utils/studioArticleForm'

const form: ArticleFormModel = {
  title: '  Studio signal  ', slug: 'studio-signal', excerpt: '摘要', seoTitle: 'SEO', seoDescription: 'SEO 描述',
  categoryId: 'category-1', tagIds: ['tag-2', 'tag-1'], coverMediaId: 'media-1', scheduledAt: '2030-01-02T03:04', markdown: '# Hello', version: 4,
}

describe('Studio article form mapping', () => {
  it('maps server response fields into editable local values', () => {
    const result = articleToForm({
      id: 'article-1', title: 'Title', slug: 'title', excerpt: null, markdown: '# Body', status: 'DRAFT',
      publishedAt: null, scheduledAt: '2030-01-02T03:04:05Z', seoTitle: null, seoDescription: 'desc', categoryId: 'category-1',
      coverMediaId: 'media-1', tagIds: ['tag-1'], createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z', version: 2,
    })
    expect(result).toMatchObject({ title: 'Title', excerpt: '', categoryId: 'category-1', coverMediaId: 'media-1', version: 2, markdown: '# Body' })
    expect(result.scheduledAt).toMatch(/^2030-01-02T/)
  })

  it('maps create and update payloads without UI-only fields', () => {
    expect(formToCreateRequest(form)).toEqual({
      title: 'Studio signal', slug: 'studio-signal', excerpt: '摘要', markdown: '# Hello', seoTitle: 'SEO', seoDescription: 'SEO 描述',
      scheduledAt: expect.any(String), categoryId: 'category-1', coverMediaId: 'media-1', tagIds: ['tag-2', 'tag-1'],
    })
    expect(formToCreateRequest(form).scheduledAt).toMatch(/^2030-01-0[12]T.*\.000Z$/)
    expect(formToUpdateRequest(form).version).toBe(4)
  })

  it('rejects invalid values before a save request can be emitted', () => {
    expect(validateArticleForm({ ...form, title: '   ' }).title).toBeTruthy()
    expect(validateArticleForm({ ...form, slug: 'Not A Slug' }).slug).toBeTruthy()
    expect(validateArticleForm({ ...form, markdown: 'x'.repeat(1024 * 1024 + 1) }).markdown).toBeTruthy()
    expect(validateArticleForm({ ...form, scheduledAt: 'not-a-date' }).scheduledAt).toBeTruthy()
  })

  it('does not confirm clean or expired-session navigation', () => {
    const confirm = () => true
    expect(shouldConfirmArticleLeave(false, true, confirm)).toBe(true)
    expect(shouldConfirmArticleLeave(true, false, () => false)).toBe(true)
    expect(shouldConfirmArticleLeave(true, true, () => false)).toBe(false)
  })

  it('ignores tag order when calculating dirty state', () => {
    expect(articleFormSnapshot(form)).toBe(articleFormSnapshot({ ...form, tagIds: ['tag-1', 'tag-2'] }))
  })
})
