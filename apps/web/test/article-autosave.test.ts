import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createArticleAutosave } from '../app/composables/useArticleAutosave'
import { AdminSessionError } from '../app/composables/useAdminSession'
import type { ArticleDraftRecord, ArticleDraftStore } from '../app/utils/articleDraftStore'
import type { ArticleFormModel } from '../app/utils/studioArticleForm'
import type { components } from '@haoblog/api-client'

type Article = components['schemas']['AdminArticleResponse']

const article = (version = 1): Article => ({
  id: 'article-1', title: 'Server title', slug: 'server-title', excerpt: null, markdown: '# Server', status: 'DRAFT',
  publishedAt: null, scheduledAt: null, seoTitle: null, seoDescription: null, categoryId: null, coverMediaId: null,
  tagIds: [], createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z', version,
  commentsEnabled: true,
})

function storeWith(record: ArticleDraftRecord | null = null) {
  let current = record
  return {
    get: vi.fn(async () => current),
    put: vi.fn(async (next: ArticleDraftRecord) => { current = next }),
    delete: vi.fn(async () => { current = null }),
  } satisfies ArticleDraftStore
}

function localRecord(baseVersion = 0): ArticleDraftRecord {
  const form: ArticleFormModel = {
    title: 'Local title', slug: 'local-title', excerpt: '', seoTitle: '', seoDescription: '', categoryId: '', tagIds: [],
    coverMediaId: null, scheduledAt: '', markdown: '# Local', version: baseVersion,
    commentsEnabled: true,
  }
  return { articleId: 'article-1', form, baseVersion, localUpdatedAt: '2030-01-02T00:00:00.000Z' }
}

describe('article autosave coordinator', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('checks dirty state only at 15 seconds', async () => {
    const save = vi.fn().mockResolvedValue(article(2))
    const autosave = createArticleAutosave({ article: article(), saveArticle: save, store: storeWith() })
    autosave.start()
    autosave.form.title = 'Changed'

    await vi.advanceTimersByTimeAsync(14_999)
    expect(save).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    await vi.runAllTicks()
    expect(save).toHaveBeenCalledTimes(1)
    autosave.stop()
  })

  it('serializes requests and keeps edits made while saving for the next round', async () => {
    let resolveFirst: ((value: Article) => void) | undefined
    const first = new Promise<Article>(resolve => { resolveFirst = resolve })
    const save = vi.fn().mockReturnValueOnce(first).mockResolvedValueOnce(article(3))
    const autosave = createArticleAutosave({ article: article(), saveArticle: save, store: storeWith() })
    autosave.start()
    autosave.form.title = 'First'
    await vi.advanceTimersByTimeAsync(15_000)
    expect(save).toHaveBeenCalledTimes(1)

    autosave.form.title = 'Second'
    await vi.advanceTimersByTimeAsync(15_000)
    expect(save).toHaveBeenCalledTimes(1)

    resolveFirst?.(article(2))
    await vi.runAllTicks()
    await vi.advanceTimersByTimeAsync(15_000)
    await vi.runAllTicks()
    expect(save).toHaveBeenCalledTimes(2)
    expect(save.mock.calls[1][0]).toMatchObject({ title: 'Second', version: 2 })
    autosave.stop()
  })

  it('restores old copies in review mode and requires explicit adoption', async () => {
    const store = storeWith(localRecord())
    const save = vi.fn().mockResolvedValue(article(2))
    const autosave = createArticleAutosave({ article: article(1), saveArticle: save, store })
    autosave.start()
    await vi.runAllTicks()
    expect(autosave.recovery.value?.review).toBe(true)

    autosave.restoreLocalCopy()
    expect(autosave.recoveryReview.value).toBe(true)
    await vi.advanceTimersByTimeAsync(15_000)
    expect(save).not.toHaveBeenCalled()

    await autosave.adoptLocalCopy()
    expect(autosave.form.version).toBe(1)
    await vi.advanceTimersByTimeAsync(15_000)
    await vi.runAllTicks()
    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0][0]).toMatchObject({ title: 'Local title', version: 1 })
    autosave.stop()
  })

  it('discards a local copy and returns to the server form', async () => {
    const store = storeWith(localRecord(1))
    const autosave = createArticleAutosave({ article: article(1), saveArticle: vi.fn(), store })
    autosave.start()
    await vi.runAllTicks()
    autosave.restoreLocalCopy()
    await autosave.discardLocalCopy()
    expect(autosave.form.title).toBe('Server title')
    expect(autosave.dirty.value).toBe(false)
    expect(store.delete).toHaveBeenCalledWith('article-1')
    autosave.stop()
  })

  it('keeps the local copy and does not claim synced after failures or 409', async () => {
    const store = storeWith()
    const save = vi.fn().mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new AdminSessionError(409, { code: 'ARTICLE_VERSION_CONFLICT', title: 'conflict', detail: 'stale', traceId: 'trace-1', currentVersion: 2 }))
    const autosave = createArticleAutosave({ article: article(), saveArticle: save, store })
    autosave.start()
    autosave.form.title = 'Changed'
    await vi.advanceTimersByTimeAsync(15_000)
    await vi.runAllTicks()
    expect(autosave.status.value).toBe('error')
    expect(autosave.localCopyPresent.value).toBe(true)
    expect(store.delete).not.toHaveBeenCalled()

    await autosave.saveNow(true)
    expect(autosave.status.value).toBe('conflict')
    await vi.advanceTimersByTimeAsync(30_000)
    expect(save).toHaveBeenCalledTimes(2)
    autosave.stop()
  })
})
