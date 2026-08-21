import MarkdownIt from 'markdown-it'
import sanitizeHtml from 'sanitize-html'
import type { components } from '@haoblog/api-client'
import type { Token } from 'markdown-it'
import type { PublicArticleContent } from '../../app/utils/publicArticleContent'

type Article = components['schemas']['ArticleResponse']
type TocItem = PublicArticleContent['toc'][number]

const LINK_CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/

function isAllowedLink(value: string | undefined) {
  if (!value || LINK_CONTROL_CHARACTERS.test(value)) return false
  const url = value.trim()
  if (/^\/(?!\/)/.test(url) && !url.includes('\\')) return true
  if (/^mailto:[^\s]+$/i.test(url)) return true
  if (!/^https:\/\//i.test(url)) return false
  try {
    return new URL(url).protocol === 'https:'
  } catch {
    return false
  }
}

function isAllowedImage(value: string | undefined) {
  if (!value || LINK_CONTROL_CHARACTERS.test(value) || !/^https:\/\//i.test(value.trim())) return false
  try {
    return new URL(value.trim()).protocol === 'https:'
  } catch {
    return false
  }
}

function headingLabel(token: Token) {
  return (token.children || []).map(child => {
    if (child.type === 'image') return child.attrGet('alt') || ''
    return String(child.content)
  }).join('').trim()
}

function headingId(label: string, seen: Map<string, number>) {
  const base = label.normalize('NFKC').toLowerCase().replace(/[^\p{Letter}\p{Number}]+/gu, '-').replace(/^-|-$/g, '') || 'section'
  const count = (seen.get(base) || 0) + 1
  seen.set(base, count)
  return count === 1 ? base : `${base}-${count}`
}

function createMarkdownRenderer(toc: TocItem[]) {
  const seenIds = new Map<string, number>()
  const markdown = new MarkdownIt({
    html: false,
    linkify: false,
    typographer: false,
  })

  markdown.validateLink = isAllowedLink
  markdown.renderer.rules.heading_open = (tokens, index, options, env, self) => {
    const token = tokens[index]!
    const originalLevel = Number(token.tag.slice(1))
    const finalLevel = Math.min(6, originalLevel + 1)
    const label = headingLabel(tokens[index + 1]!)
    token.tag = `h${finalLevel}`
    token.attrSet('id', headingId(label, seenIds))
    const id = token.attrGet('id')
    if ((finalLevel === 2 || finalLevel === 3) && typeof id === 'string') toc.push({ id, label, level: finalLevel })
    return self.renderToken(tokens, index, options)
  }
  markdown.renderer.rules.heading_close = (tokens, index, options, env, self) => {
    const token = tokens[index]!
    token.tag = `h${Math.min(6, Number(token.tag.slice(1)) + 1)}`
    return self.renderToken(tokens, index, options)
  }
  markdown.renderer.rules.image = (tokens, index, options, env, self) => {
    const token = tokens[index]!
    const source = token.attrGet('src')
    const alt = token.attrGet('alt')
    const src = typeof source === 'string' ? source : source == null ? undefined : String(source)
    const altText = typeof alt === 'string' ? alt : alt == null ? '' : String(alt)
    if (!isAllowedImage(src)) return markdown.utils.escapeHtml(`![${altText}](${src || ''})`)
    token.attrSet('alt', token.content)
    token.attrSet('loading', 'lazy')
    token.attrSet('decoding', 'async')
    return self.renderToken(tokens, index, options)
  }

  return markdown
}

export function renderPublicArticleMarkdown(markdownSource: string) {
  const toc: TocItem[] = []
  const markdown = createMarkdownRenderer(toc)
  const renderedHtml = sanitizeHtml(markdown.render(markdownSource), {
    allowedTags: [
      'p', 'br', 'hr', 'h2', 'h3', 'h4', 'h5', 'h6', 'em', 'strong', 'del', 's',
      'a', 'img', 'blockquote', 'ul', 'ol', 'li', 'pre', 'code',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
    ],
    allowedAttributes: {
      h2: ['id'], h3: ['id'], h4: ['id'], h5: ['id'], h6: ['id'],
      a: ['href', 'title'],
      img: ['src', 'alt', 'title', 'loading', 'decoding'],
      code: ['class'],
      th: ['colspan', 'rowspan'], td: ['colspan', 'rowspan'],
    },
    allowedClasses: { code: [/^language-[\w-]+$/] },
    allowedSchemes: ['https', 'mailto'],
    allowedSchemesByTag: { a: ['https', 'mailto'], img: ['https'] },
    allowedSchemesAppliedToAttributes: ['href', 'src'],
    allowProtocolRelative: false,
    disallowedTagsMode: 'escape',
    transformTags: {
      a: (tagName, attributes) => isAllowedLink(attributes.href)
        ? { tagName, attribs: attributes }
        : { tagName: 'span', attribs: {} },
    },
  })
  return { renderedHtml, toc }
}

export function buildPublicArticleContent(article: Article): PublicArticleContent {
  const { renderedHtml, toc } = renderPublicArticleMarkdown(article.markdown)
  return {
    article,
    renderedHtml,
    toc,
    hasMermaid: /(^|\n)```mermaid(?:\s|$)/im.test(article.markdown),
  }
}

export type PublicArticleContentFetchResult = {
  status: number
  headers: Headers
  body?: PublicArticleContent
}

export async function fetchPublicArticleContent(
  slug: string,
  apiBaseUrl: string,
  ifNoneMatch: string | undefined,
  fetcher: typeof fetch = fetch,
): Promise<PublicArticleContentFetchResult> {
  const target = new URL(`/api/v1/public/articles/${encodeURIComponent(slug)}`, apiBaseUrl)
  const headers = new Headers()
  if (ifNoneMatch) headers.set('if-none-match', ifNoneMatch)
  const response = await fetcher(target, { headers })
  const forwardedHeaders = new Headers()
  for (const name of ['etag', 'cache-control']) {
    const value = response.headers.get(name)
    if (value) forwardedHeaders.set(name, value)
  }
  if (response.status === 304 || response.status === 404) return { status: response.status, headers: forwardedHeaders }
  if (!response.ok) return { status: 502, headers: forwardedHeaders }
  return { status: 200, headers: forwardedHeaders, body: buildPublicArticleContent(await response.json() as Article) }
}
