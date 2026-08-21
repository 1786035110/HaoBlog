import type { components } from '@haoblog/api-client'

export type PublicArticleContent = {
  article: components['schemas']['ArticleResponse']
  renderedHtml: string
  toc: Array<{ id: string; label: string; level: number }>
  hasMermaid: boolean
}
