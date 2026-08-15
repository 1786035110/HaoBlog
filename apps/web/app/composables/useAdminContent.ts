import type { components } from '@haoblog/api-client'
import { useAdminSession } from './useAdminSession'

type ArticleList = components['schemas']['AdminArticleListResponse']
type ArticleSummary = components['schemas']['AdminArticleSummary']
type Article = components['schemas']['AdminArticleResponse']
type CreateRequest = components['schemas']['AdminArticleCreateRequest']
type UpdateRequest = components['schemas']['AdminArticleUpdateRequest']
type Category = components['schemas']['CategoryResponse']
type Tag = components['schemas']['TagResponse']

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

  async function listCategories() {
    return session.request<Category[]>('/api/v1/admin/categories')
  }

  async function listTags() {
    return session.request<Tag[]>('/api/v1/admin/tags')
  }

  return { listArticles, getArticle, createArticle, updateArticle, listCategories, listTags }
}

export type { Article, ArticleSummary, Category, CreateRequest, Tag, UpdateRequest }
