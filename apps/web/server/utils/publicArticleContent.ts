import MarkdownIt from 'markdown-it'
import sanitizeHtml from 'sanitize-html'
import type { components } from '@haoblog/api-client'
import type { Token } from 'markdown-it'
import { createHighlighterCoreSync } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'
import bash from '@shikijs/langs/bash'
import css from '@shikijs/langs/css'
import dockerfile from '@shikijs/langs/dockerfile'
import html from '@shikijs/langs/html'
import java from '@shikijs/langs/java'
import javascript from '@shikijs/langs/javascript'
import json from '@shikijs/langs/json'
import kotlin from '@shikijs/langs/kotlin'
import markdown from '@shikijs/langs/markdown'
import powershell from '@shikijs/langs/powershell'
import shellscript from '@shikijs/langs/shellscript'
import sql from '@shikijs/langs/sql'
import typescript from '@shikijs/langs/typescript'
import vue from '@shikijs/langs/vue'
import xml from '@shikijs/langs/xml'
import yaml from '@shikijs/langs/yaml'
import darkPlus from '@shikijs/themes/dark-plus'
import lightPlus from '@shikijs/themes/light-plus'
import type { PublicArticleContent } from '../../app/utils/publicArticleContent'

type Article = components['schemas']['ArticleResponse']
type TocItem = PublicArticleContent['toc'][number]

const LINK_CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/
const MAX_METADATA_LINE = 100_000

const shikiHighlighter = (() => {
  try {
    return createHighlighterCoreSync({
      langs: [bash, shellscript, powershell, java, kotlin, xml, html, css, javascript, typescript, json, yaml, sql, vue, markdown, dockerfile],
      themes: [darkPlus, lightPlus],
      engine: createJavaScriptRegexEngine({ forgiving: true }),
    })
  } catch {
    return null
  }
})()

const LANGUAGE_ALIASES: Record<string, string | undefined> = {
  plaintext: 'plaintext',
  text: 'plaintext',
  bash: 'bash',
  sh: 'shellscript',
  shell: 'shellscript',
  shellscript: 'shellscript',
  powershell: 'powershell',
  ps: 'powershell',
  ps1: 'powershell',
  pwsh: 'powershell',
  java: 'java',
  kotlin: 'kotlin',
  kt: 'kotlin',
  xml: 'xml',
  html: 'html',
  css: 'css',
  javascript: 'javascript',
  js: 'javascript',
  typescript: 'typescript',
  ts: 'typescript',
  json: 'json',
  yaml: 'yaml',
  yml: 'yaml',
  sql: 'sql',
  vue: 'vue',
  markdown: 'markdown',
  md: 'markdown',
  dockerfile: 'dockerfile',
  docker: 'dockerfile',
}

type CodeMetadata = {
  rawLanguage: string
  language: string | undefined
  filename?: string
  focusRanges: Array<[number, number]>
}

const HTML_ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, character => HTML_ESCAPES[character]!)
}

function parseFocusRanges(value: string) {
  const ranges: Array<[number, number]> = []
  for (const part of value.split(',')) {
    const range = part.trim().match(/^(\d+)(?:-(\d+))?$/)
    if (!range) continue
    const start = Number(range[1])
    const end = Number(range[2] || range[1])
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 1 || end < start || end > MAX_METADATA_LINE) continue
    ranges.push([start, end])
  }
  return ranges
}

function parseCodeMetadata(info: string): CodeMetadata {
  const parts = info.trim().split(/\s+/).filter(Boolean)
  const rawLanguage = (parts.shift() || 'plaintext').toLowerCase()
  let filename: string | undefined
  const focusRanges: Array<[number, number]> = []
  for (const part of parts) {
    if (!filename && /^\[[^\]]+\]$/.test(part)) {
      filename = part.slice(1, -1)
      continue
    }
    if (/^\{[^{}]*\}$/.test(part)) focusRanges.push(...parseFocusRanges(part.slice(1, -1)))
  }
  return { rawLanguage, language: LANGUAGE_ALIASES[rawLanguage], filename, focusRanges }
}

function focusedLines(ranges: Array<[number, number]>, lineCount: number) {
  const focused = new Set<number>()
  for (const [start, end] of ranges) {
    for (let line = start; line <= Math.min(end, lineCount); line++) focused.add(line)
  }
  return focused
}

function decorateLines(html: string, focusRanges: Array<[number, number]>, lineCount: number) {
  const focused = focusedLines(focusRanges, lineCount)
  let lineNumber = 0
  return html.replace(/<span class="line">/g, () => {
    lineNumber += 1
    return `<span class="line${focused.has(lineNumber) ? ' is-focused' : ''}" data-line="${lineNumber}">`
  })
}

function renderPlainCode(code: string, focusRanges: Array<[number, number]>) {
  const lines = code.split('\n')
  const focused = focusedLines(focusRanges, lines.length)
  return `<pre class="shiki code-highlight-fallback" tabindex="0"><code>${lines.map((line, index) => `<span class="line${focused.has(index + 1) ? ' is-focused' : ''}" data-line="${index + 1}">${escapeHtml(line)}</span>`).join('')}</code></pre>`
}

function renderCodeFence(token: Token) {
  const metadata = parseCodeMetadata(token.info || '')
  const code = token.content.replace(/\r\n?/g, '\n').replace(/\n$/, '')
  const lineCount = Math.max(1, code.split('\n').length)
  const language = metadata.language || 'plaintext'
  const languageClass = /^[a-z0-9_-]+$/.test(metadata.rawLanguage) ? metadata.rawLanguage : 'plaintext'
  let codeHtml = renderPlainCode(code, metadata.focusRanges)
  if (shikiHighlighter && language !== 'plaintext') {
    try {
      codeHtml = decorateLines(shikiHighlighter.codeToHtml(code, {
        lang: language,
        themes: { light: 'light-plus', dark: 'dark-plus' },
        defaultColor: false,
      }), metadata.focusRanges, lineCount)
    } catch {
      codeHtml = renderPlainCode(code, metadata.focusRanges)
    }
  }
  codeHtml = codeHtml.replace('<code>', `<code class="language-${languageClass}">`)
  const displayLanguage = metadata.language || metadata.rawLanguage || 'plaintext'
  const filename = metadata.filename ? `<span class="code-block-filename">${escapeHtml(metadata.filename)}</span>` : ''
  const filenameAttribute = metadata.filename ? ` data-filename="${escapeHtml(metadata.filename)}"` : ''
  return `<div class="code-block" data-code-block="true" data-language="${escapeHtml(displayLanguage)}"${filenameAttribute}><div class="code-block-header"><span class="code-block-language">${escapeHtml(displayLanguage)}</span>${filename}<button type="button" class="code-copy-button" data-code-copy="true" aria-label="复制${escapeHtml(metadata.filename ? `文件 ${metadata.filename}` : `${displayLanguage} 代码`)}">复制代码</button></div>${codeHtml}</div>`
}

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
  markdown.renderer.rules.fence = (tokens, index) => renderCodeFence(tokens[index]!)

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
      'div', 'span', 'button',
    ],
    allowedAttributes: {
      h2: ['id'], h3: ['id'], h4: ['id'], h5: ['id'], h6: ['id'],
      a: ['href', 'title'],
      img: ['src', 'alt', 'title', 'loading', 'decoding'],
      pre: ['class', 'tabindex', 'style'],
      code: ['class'],
      div: ['class', 'data-code-block', 'data-language', 'data-filename'],
      span: ['class', 'data-line', 'style'],
      button: ['type', 'class', 'data-code-copy', 'aria-label'],
      th: ['colspan', 'rowspan'], td: ['colspan', 'rowspan'],
    },
    allowedClasses: {
      code: [/^language-[\w-]+$/],
      pre: [/^shiki$/, /^shiki-themes$/, /^light-plus$/, /^dark-plus$/, /^code-highlight-fallback$/],
      div: [/^code-block$/, /^code-block-header$/],
      span: [/^code-block-language$/, /^code-block-filename$/, /^line$/, /^is-focused$/],
      button: [/^code-copy-button$/],
    },
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
