export type PublicArticleSeoInput = {
  title: string
  excerpt?: string | null
  seoTitle?: string | null
  seoDescription?: string | null
  coverImageUrl?: string | null
}

function absoluteHttpUrl(value: string | null | undefined, origin: string, fallback: string) {
  try {
    const url = new URL(value || fallback, origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : new URL(fallback, origin).toString()
  } catch {
    return new URL(fallback, origin).toString()
  }
}

export function buildPublicArticleSeo(article: PublicArticleSeoInput, origin: string) {
  const title = article.seoTitle?.trim() || article.title
  const description = article.seoDescription?.trim() || article.excerpt?.trim() || article.title
  const image = absoluteHttpUrl(article.coverImageUrl, origin, '/og-default.svg')
  return { title, description, image }
}
