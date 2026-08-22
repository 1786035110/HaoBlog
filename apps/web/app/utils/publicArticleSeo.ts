export type PublicArticleSeoInput = {
  id?: string
  slug?: string
  title: string
  excerpt?: string | null
  seoTitle?: string | null
  seoDescription?: string | null
  coverImageUrl?: string | null
  publishedAt?: string
  modifiedAt?: string
}

export type PublicSeoSite = {
  title: string
  siteUrl: string
  authorName: string
}

function configuredSiteUrl(siteUrl: string) {
  try {
    const url = new URL(siteUrl)
    if ((url.protocol !== 'http:' && url.protocol !== 'https:') || !url.hostname || url.username || url.password || url.search || url.hash) return null
    return url.toString().replace(/\/$/, '')
  } catch {
    return null
  }
}

export function publicAbsoluteUrl(siteUrl: string, path: string) {
  const base = configuredSiteUrl(siteUrl)
  if (!base) return null
  try {
    return new URL(path, `${base}/`).toString()
  } catch {
    return null
  }
}

function absoluteHttpsUrl(value: string | null | undefined) {
  if (!value?.trim()) return null
  try {
    const url = new URL(value.trim())
    return url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

export function buildPublicArticleSeo(article: PublicArticleSeoInput, siteUrl: string) {
  const title = article.seoTitle?.trim() || article.title
  const description = article.seoDescription?.trim() || article.excerpt?.trim() || article.title
  const image = absoluteHttpsUrl(article.coverImageUrl) || publicAbsoluteUrl(siteUrl, '/og-default.svg')
  return { title, description, image }
}

export function buildPublicPageSeo(site: PublicSeoSite, path: string, title: string, description: string, type: 'website' | 'article' = 'website') {
  const url = publicAbsoluteUrl(site.siteUrl, path)
  const image = publicAbsoluteUrl(site.siteUrl, '/og-default.svg')
  return {
    title,
    description,
    url,
    image,
    type,
    siteName: site.title,
  }
}

export function serializeJsonLd(value: unknown) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003C')
    .replace(/>/g, '\\u003E')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
}

export function buildPublicArticleJsonLd(site: PublicSeoSite, article: PublicArticleSeoInput) {
  const url = article.slug ? publicAbsoluteUrl(site.siteUrl, `/articles/${encodeURIComponent(article.slug)}`) : null
  const seo = buildPublicArticleSeo(article, site.siteUrl)
  return {
    '@context': 'https://schema.org',
    '@type': 'BlogPosting',
    headline: seo.title,
    description: seo.description,
    url,
    mainEntityOfPage: url ? { '@type': 'WebPage', '@id': url } : undefined,
    author: { '@type': 'Person', name: site.authorName },
    publisher: { '@type': 'Organization', name: site.title, url: site.siteUrl },
    datePublished: article.publishedAt,
    dateModified: article.modifiedAt || article.publishedAt,
    image: seo.image || undefined,
    inLanguage: 'zh-CN',
  }
}

export function publicPageHead(seo: ReturnType<typeof buildPublicPageSeo>, robots = 'index,follow') {
  return {
    title: seo.title,
    meta: [
      { name: 'description', content: seo.description },
      { name: 'robots', content: robots },
      { property: 'og:type', content: seo.type },
      { property: 'og:title', content: seo.title },
      { property: 'og:description', content: seo.description },
      { property: 'og:url', content: seo.url || undefined },
      { property: 'og:site_name', content: seo.siteName },
      { property: 'og:locale', content: 'zh_CN' },
      { property: 'og:image', content: seo.image || undefined },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: seo.title },
      { name: 'twitter:description', content: seo.description },
      { name: 'twitter:image', content: seo.image || undefined },
    ],
    link: seo.url ? [{ rel: 'canonical' as const, href: seo.url }] : [],
  }
}
