import type { components } from '@haoblog/api-client'

type ArticleList = components['schemas']['ArticleListResponse']

export function parsePublicArticlePage(value: string | (string | null)[] | null | undefined) {
  if (value === undefined) return { page: 1, canonical: false }
  if (value === null || Array.isArray(value) || !/^[1-9]\d*$/.test(value)) return null

  const page = Number(value)
  if (!Number.isSafeInteger(page)) return null
  return { page, canonical: page === 1 && value === '1' }
}

export function isPublicArticlePageOutOfRange(page: number, result: ArticleList) {
  return page > 1 && result.items.length === 0
}

export function publicArticlePageUrl(page: number) {
  return page === 1 ? '/articles' : `/articles?page=${page}`
}
