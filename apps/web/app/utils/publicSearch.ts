import type { components } from '@haoblog/api-client'

type ArticleList = components['schemas']['ArticleListResponse']

export function normalizePublicSearchQuery(value: string | null | undefined) {
  if (typeof value !== 'string') return null
  const normalized = value.normalize('NFKC').trim()
  const length = Array.from(normalized).length
  return length >= 2 && length <= 100 ? normalized : null
}

export function publicSearchPageUrl(query: string, page: number) {
  const params = new URLSearchParams({ q: query })
  if (page > 1) params.set('page', String(page))
  return `/search?${params.toString()}`
}

export function isPublicSearchPageOutOfRange(page: number, result: ArticleList) {
  return page > 1 && result.items.length === 0
}
