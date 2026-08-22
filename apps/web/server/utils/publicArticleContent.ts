import MarkdownIt from 'markdown-it'
import sanitizeHtml from 'sanitize-html'
import type { components } from '@haoblog/api-client'
import type { Token } from 'markdown-it'
import { container } from '@mdit/plugin-container'
import { footnote } from '@mdit/plugin-footnote'
import { katex } from '@mdit/plugin-katex'
import { tasklist } from '@mdit/plugin-tasklist'
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
type PublicArticleRenderOptions = { saveData?: boolean }

const LINK_CONTROL_CHARACTERS = /[\u0000-\u001f\u007f]/
const MAX_METADATA_LINE = 100_000
const KATEX_MAX_SIZE = 10
const KATEX_MAX_EXPAND = 100

const CALLOUTS = {
  note: { label: '注记', role: 'note' },
  tip: { label: '提示', role: 'note' },
  warning: { label: '警告', role: 'alert' },
  danger: { label: '危险', role: 'alert' },
} as const

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

function renderMermaidFence(code: string) {
  return `<figure class="mermaid-figure" data-mermaid-figure="true" data-mermaid-state="source"><figcaption class="mermaid-caption">MERMAID / DIAGRAM <button type="button" class="mermaid-render-button" data-mermaid-render="true" aria-label="渲染 Mermaid 图表">渲染图表</button><span class="mermaid-status" data-mermaid-status="true" role="status" aria-live="polite"></span></figcaption><div class="mermaid-output" data-mermaid-output="true" hidden aria-live="polite"></div><pre class="mermaid-source" data-mermaid-source="true" tabindex="0"><code class="language-mermaid">${escapeHtml(code)}</code></pre></figure>`
}

function renderCodeFence(token: Token) {
  const metadata = parseCodeMetadata(token.info || '')
  const code = token.content.replace(/\r\n?/g, '\n').replace(/\n$/, '')
  if (metadata.rawLanguage === 'mermaid') return renderMermaidFence(code)
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
  return `<div class="code-block" data-code-block="true" data-language="${escapeHtml(displayLanguage)}"${filenameAttribute}><div class="code-block-header"><span class="code-block-language">${escapeHtml(displayLanguage)}</span>${filename}<button type="button" class="code-copy-button" data-code-copy="true" aria-label="复制代码：${escapeHtml(metadata.filename || displayLanguage)}">复制代码</button></div>${codeHtml}</div>`
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

function isAllowedFootnoteLink(value: string | undefined) {
  return typeof value === 'string' && /^#footnote(?:-ref)?\d+(?::\d+)?$/i.test(value)
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

function renderCalloutOpen(name: keyof typeof CALLOUTS, tokens: Token[], index: number) {
  const token = tokens[index]!
  const title = token.info.trim().replace(new RegExp(`^${name}\\b`, 'i'), '').trim()
  const callout = CALLOUTS[name]
  const heading = title ? `${callout.label}：${title}` : callout.label
  return `<div class="markdown-callout markdown-callout--${name}" role="${callout.role}"><p class="markdown-callout-title">${escapeHtml(heading)}</p>\n`
}

function renderKatexOutput(content: string, displayMode: boolean) {
  const hasError = content.includes('katex-error')
  let safeContent = content
  if (hasError) {
    safeContent = safeContent.replace(/\s+title=['"][^'"]*['"]/, ' title="公式解析失败"')
    if (!safeContent.includes('title="公式解析失败"')) {
      safeContent = safeContent.replace(/(<(?:span|p)[^>]*class=['"][^'"]*\bkatex-error\b[^'"]*['"])/, '$1 title="公式解析失败"')
    }
  }
  if (!displayMode) return safeContent
  return safeContent
    .replace(/^<p class=['"]katex-block(?:\s+katex-error)?['"]([^>]*)>/, `<div class="katex-block${hasError ? ' katex-error' : ''}"$1 role="group" aria-label="数学公式">`)
    .replace(/<\/p>\n?$/, '</div>\n')
}

function createMarkdownRenderer(toc: TocItem[]) {
  const seenIds = new Map<string, number>()
  const markdown = new MarkdownIt({
    html: false,
    linkify: false,
    typographer: false,
  })

  markdown
    .use(footnote)
    .use(tasklist, { disabled: true, label: true })
    .use(katex, {
      delimiters: 'all',
      output: 'htmlAndMathml',
      trust: false,
      strict: 'error',
      throwOnError: false,
      maxSize: KATEX_MAX_SIZE,
      maxExpand: KATEX_MAX_EXPAND,
      globalGroup: false,
      macros: {},
      logger: () => 'error' as const,
      transformer: renderKatexOutput,
    })

  for (const name of Object.keys(CALLOUTS) as Array<keyof typeof CALLOUTS>) {
    markdown.use(container, {
      name,
      openRenderer: (tokens, index) => renderCalloutOpen(name, tokens, index),
      closeRenderer: () => '</div>\n',
    })
  }

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
  markdown.renderer.rules.table_open = () => '<div class="markdown-table-scroll" tabindex="0" role="region" aria-label="可横向滚动的表格">\n<table>\n'
  markdown.renderer.rules.table_close = () => '</table>\n</div>\n'

  return markdown
}

export function renderPublicArticleMarkdown(markdownSource: string, options: PublicArticleRenderOptions = {}) {
  const toc: TocItem[] = []
  const hasMermaid = /^(?: {0,3})```[ \t]*mermaid(?:[ \t]|$)/im.test(markdownSource)
  const markdown = createMarkdownRenderer(toc)
  let rawHtml: string
  try {
    rawHtml = markdown.render(markdownSource)
  } catch {
    toc.length = 0
    rawHtml = `<pre class="markdown-render-error"><code>${escapeHtml(markdownSource)}</code></pre>`
  }
  const renderedHtml = sanitizeHtml(rawHtml, {
    allowedTags: [
      'p', 'br', 'hr', 'h2', 'h3', 'h4', 'h5', 'h6', 'em', 'strong', 'del', 's',
      'a', 'img', 'blockquote', 'ul', 'ol', 'li', 'pre', 'code', 'input', 'label', 'sup', 'section', 'figure', 'figcaption',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'div', 'span', 'button', 'math', 'semantics', 'mrow', 'mfrac', 'mn', 'mi', 'mo', 'msup', 'msub',
      'msubsup', 'munder', 'mover', 'munderover', 'msqrt', 'mroot', 'mtable', 'mtr', 'mtd', 'mpadded',
      'mstyle', 'mspace', 'menclose', 'mtext', 'annotation', 'svg', 'path',
    ],
    allowedAttributes: {
      h2: ['id'], h3: ['id'], h4: ['id'], h5: ['id'], h6: ['id'],
      p: ['class', 'title', 'role'],
      a: ['href', 'title', 'class', 'id'],
      img: ['src', 'alt', 'title', 'loading', 'decoding'],
      pre: ['class', 'tabindex', 'data-mermaid-source'],
      code: ['class'],
      div: ['class', 'data-code-block', 'data-language', 'data-filename', 'data-mermaid-output', 'tabindex', 'role', 'aria-label', 'aria-live', 'hidden'],
      span: ['class', 'data-line', 'data-mermaid-status', 'style', 'title', 'aria-hidden', 'role', 'aria-live'],
      button: ['type', 'class', 'data-code-copy', 'data-mermaid-render', 'aria-label'],
      figure: ['class', 'data-mermaid-figure', 'data-mermaid-state'],
      figcaption: ['class'],
      ul: ['class'],
      ol: ['class'],
      li: ['id', 'class'],
      hr: ['class'],
      section: ['class'],
      sup: ['class'],
      input: ['type', 'class', 'id', 'checked', 'disabled'],
      label: ['class', 'for'],
      th: ['colspan', 'rowspan'], td: ['colspan', 'rowspan'],
      math: ['xmlns', 'display'],
      annotation: ['encoding'],
      svg: ['xmlns', 'width', 'height', 'viewBox', 'preserveAspectRatio'],
      path: ['d'],
    },
    allowedClasses: {
      p: [/^katex-block$/, /^markdown-callout-title$/, /^markdown-image-placeholder$/],
      code: [/^language-[\w-]+$/],
      pre: [/^shiki$/, /^shiki-themes$/, /^light-plus$/, /^dark-plus$/, /^code-highlight-fallback$/, /^markdown-render-error$/, /^mermaid-source$/],
      div: [/^code-block$/, /^code-block-header$/, /^markdown-callout$/, /^markdown-callout--(?:note|tip|warning|danger)$/, /^markdown-table-scroll$/, /^katex-block$/, /^katex-error$/, /^mermaid-output$/],
      figure: [/^mermaid-figure$/],
      figcaption: [/^mermaid-caption$/],
      span: [
        /^code-block-language$/, /^code-block-filename$/, /^line$/, /^is-focused$/, /^mermaid-status$/,
        /^(?:katex|katex-[a-z-]+|mord|mop|mbin|mrel|mopen|mclose|mpunct|minner|mfrac|frac-line|mspace|msupsub|mtight|vlist(?:-[a-z0-9]+)?|nulldelimiter|reset-size\d+|size\d+|mathnormal|op-limits|op-symbol|large-op|pstrut|svg-align|hide-tail|katex-sizing)$/,
      ],
      button: [/^code-copy-button$/, /^mermaid-render-button$/],
      ul: [/^task-list-container$/],
      li: [/^footnote-item$/, /^task-list-item$/],
      label: [/^task-list-item-label$/],
      input: [/^task-list-item-checkbox$/],
      hr: [/^footnotes-sep$/],
      section: [/^footnotes$/],
      ol: [/^footnotes-list$/],
      sup: [/^footnote-ref$/],
      a: [/^footnote-anchor$/, /^footnote-backref$/],
    },
    allowedSchemes: ['https', 'mailto'],
    allowedSchemesByTag: { a: ['https', 'mailto'], img: ['https'] },
    allowedSchemesAppliedToAttributes: ['href', 'src'],
    allowProtocolRelative: false,
    disallowedTagsMode: 'escape',
    transformTags: {
      a: (tagName, attributes) => (isAllowedLink(attributes.href) || isAllowedFootnoteLink(attributes.href))
        ? { tagName, attribs: attributes }
        : { tagName: 'span', attribs: {} },
    },
  })
  if (!options.saveData) return { renderedHtml, toc, hasMermaid }
  const imagePlaceholder = (imageHtml: string) => {
    const alt = imageHtml.match(/\balt="([^"]*)"/i)?.[1] || '未命名图像'
    return `<p class="markdown-image-placeholder" role="note">图像已按 Save-Data 降级：${escapeHtml(alt)}</p>`
  }
  const renderedHtmlWithoutStandaloneImages = renderedHtml.replace(/<p>\s*(<img\b[^>]*>)\s*<\/p>/gi, (_match, imageHtml: string) => imagePlaceholder(imageHtml))
  return {
    renderedHtml: renderedHtmlWithoutStandaloneImages.replace(/<img\b[^>]*>/gi, imagePlaceholder),
    toc,
    hasMermaid,
  }
}

export function buildPublicArticleContent(article: Article, options: PublicArticleRenderOptions = {}): PublicArticleContent {
  const { renderedHtml, toc, hasMermaid } = renderPublicArticleMarkdown(article.markdown, options)
  return {
    article,
    renderedHtml,
    toc,
    hasMermaid,
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
  saveData = false,
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
  return { status: 200, headers: forwardedHeaders, body: buildPublicArticleContent(await response.json() as Article, { saveData }) }
}
