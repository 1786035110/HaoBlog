import type { components } from '@haoblog/api-client'
import { useAdminSession } from './useAdminSession'

type ArticleList = components['schemas']['AdminArticleListResponse']
type ArticleSummary = components['schemas']['AdminArticleSummary']
type Article = components['schemas']['AdminArticleResponse']
type CreateRequest = components['schemas']['AdminArticleCreateRequest']
type UpdateRequest = components['schemas']['AdminArticleUpdateRequest']
type Category = components['schemas']['CategoryResponse']
type Tag = components['schemas']['TagResponse']
type ArticleVersionList = components['schemas']['AdminArticleVersionListResponse']
type ArticleVersion = components['schemas']['AdminArticleVersionResponse']
type ActionResponse = components['schemas']['ArticleActionResponse']
type PreviewToken = components['schemas']['PreviewTokenResponse']

export function useAdminContent() {
  const session = useAdminSession()

  async function listArticles(params: { page?: number; size?: number; status?: components['schemas']['ArticleStatus']; keyword?: string; direction?: 'asc' | 'desc' } = {}) {
    const query = new URLSearchParams()
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    query.set('direction', params.direction ?? 'desc')
    if (params.status) query.set('status', params.status)
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim())
    return session.request<ArticleList>(`/api/v1/admin/articles?${query}`)
  }

  async function getArticle(id: string) {
    return session.request<Article>(`/api/v1/admin/articles/${encodeURIComponent(id)}`)
  }

  async function listArticleVersions(id: string, params: { page?: number; size?: number } = {}) {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) })
    return session.request<ArticleVersionList>(`/api/v1/admin/articles/${encodeURIComponent(id)}/versions?${query}`)
  }

  async function getArticleVersion(id: string, revisionId: string) {
    return session.request<ArticleVersion>(`/api/v1/admin/articles/${encodeURIComponent(id)}/versions/${encodeURIComponent(revisionId)}`)
  }

  async function restoreArticleVersion(id: string, revisionId: string, version: number) {
    return session.write<Article>(`/api/v1/admin/articles/${encodeURIComponent(id)}/versions/${encodeURIComponent(revisionId)}/restore`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ version }),
    })
  }

  async function createArticle(payload: CreateRequest) {
    return session.write<Article>('/api/v1/admin/articles', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  }

  async function updateArticle(id: string, payload: UpdateRequest) {
    return session.write<Article>(`/api/v1/admin/articles/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  }

  async function publishArticle(id: string, version: number) {
    return session.write<ActionResponse>(`/api/v1/admin/articles/${encodeURIComponent(id)}/publish`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version }),
    })
  }

  async function scheduleArticle(id: string, version: number, scheduledAt: string) {
    return session.write<ActionResponse>(`/api/v1/admin/articles/${encodeURIComponent(id)}/schedule`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version, scheduledAt }),
    })
  }

  async function createPreviewToken(id: string, version: number) {
    return session.write<PreviewToken>(`/api/v1/admin/articles/${encodeURIComponent(id)}/preview-tokens`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version }),
    })
  }

  async function listCategories() {
    return session.request<Category[]>('/api/v1/admin/categories')
  }

  async function listTags() {
    return session.request<Tag[]>('/api/v1/admin/tags')
  }

  return { listArticles, getArticle, listArticleVersions, getArticleVersion, restoreArticleVersion, createArticle, updateArticle, publishArticle, scheduleArticle, createPreviewToken, listCategories, listTags }
}

export type { ActionResponse, Article, ArticleSummary, ArticleVersion, ArticleVersionList, Category, CreateRequest, PreviewToken, Tag, UpdateRequest }
