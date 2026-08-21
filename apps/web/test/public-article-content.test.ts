import { describe, expect, it, vi } from 'vitest'
import { buildPublicArticleContent, fetchPublicArticleContent, renderPublicArticleMarkdown } from '../server/utils/publicArticleContent'
import type { components } from '@haoblog/api-client'

const article: components['schemas']['ArticleResponse'] = {
  id: 'article-1', slug: 'security', title: 'Security', excerpt: null,
  publishedAt: '2026-01-01T00:00:00Z', modifiedAt: '2026-01-01T00:00:00Z',
  markdown: '# Security', seoTitle: null, seoDescription: null, coverImageUrl: null,
}

describe('public article content model', () => {
  it('shifts headings, creates Unicode-safe duplicate ids, and collects final h2/h3 only', () => {
    const result = renderPublicArticleMarkdown('# 中文 标题\n\n# 中文 标题\n\n# !!!\n\n## 子标题\n\n### 深层标题')
    expect(result.toc).toEqual([
      { id: '中文-标题', label: '中文 标题', level: 2 },
      { id: '中文-标题-2', label: '中文 标题', level: 2 },
      { id: 'section', label: '!!!', level: 2 },
      { id: '子标题', label: '子标题', level: 3 },
    ])
    expect(result.renderedHtml).toContain('<h2 id="中文-标题">中文 标题</h2>')
    expect(result.renderedHtml).toContain('<h4 id="深层标题">深层标题</h4>')
    expect(result.renderedHtml).not.toContain('<h1')
  })

  it('escapes raw HTML and removes unsafe protocols while preserving allowed links and images', () => {
    const result = renderPublicArticleMarkdown([
      '<script>alert(1)</script><p onclick="alert(2)">raw</p>',
      '[inside](/docs/start) [external](https://example.com) [mail](mailto:hello@example.com)',
      '[js](javascript:alert(1)) [data](data:text/html,boom) [file](file:///tmp/x) [http](http://example.com) [proto](//evil.example) [backslash](/\\evil.example)',
      '![safe](https://cdn.example.com/photo.png) ![bad](http://cdn.example.com/photo.png)',
    ].join('\n\n'))
    expect(result.renderedHtml).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(result.renderedHtml).not.toContain('<script')
    expect(result.renderedHtml).not.toMatch(/<[^>]+onclick/)
    expect(result.renderedHtml).toContain('href="/docs/start"')
    expect(result.renderedHtml).toContain('href="https://example.com"')
    expect(result.renderedHtml).toContain('href="mailto:hello@example.com"')
    expect(result.renderedHtml).not.toContain('href="javascript:')
    expect(result.renderedHtml).not.toContain('href="data:')
    expect(result.renderedHtml).not.toContain('href="file:')
    expect(result.renderedHtml).not.toContain('href="http://')
    expect(result.renderedHtml).not.toContain('href="//evil.example"')
    expect(result.renderedHtml).not.toContain('href="/\\evil.example"')
    expect(result.renderedHtml).toContain('src="https://cdn.example.com/photo.png"')
    expect(result.renderedHtml).toContain('alt="safe"')
    expect(result.renderedHtml).not.toContain('src="http://')
  })

  it('supports tables, strikethrough, ordinary fences, Mermaid detection, and malformed long input', () => {
    const result = buildPublicArticleContent({ ...article, markdown: '| A | B |\n|---|---|\n| 1 | ~~2~~ |\n\n```ts\nconst x = 1\n' })
    expect(result.renderedHtml).toContain('<table>')
    expect(result.renderedHtml).toContain('<s>2</s>')
    expect(result.renderedHtml).toContain('language-ts')
    expect(result.hasMermaid).toBe(false)

    expect(buildPublicArticleContent({ ...article, markdown: '```mermaid\nflowchart TD\nA-->B\n```' }).hasMermaid).toBe(true)
    expect(() => renderPublicArticleMarkdown(`${'['.repeat(10000)}\n${'```'.repeat(1000)}`)).not.toThrow()
  })

  it('renders inline and block KaTeX with both delimiter styles on the server', () => {
    const result = renderPublicArticleMarkdown(String.raw`Inline $E=mc^2$ and \(x^2\).

$$
\frac{1}{2}
$$

\[
\sqrt{x}
\]`)
    expect(result.renderedHtml).toContain('class="katex"')
    expect(result.renderedHtml).toContain('class="katex-block"')
    expect(result.renderedHtml).toContain('annotation')
    expect(result.renderedHtml).toContain('E=mc^2')
    expect(result.renderedHtml).toContain('\\frac{1}{2}')
    expect(result.renderedHtml).toContain('<svg')
  })

  it('keeps invalid, unsafe, and over-expanded formulas readable without throwing', () => {
    const result = renderPublicArticleMarkdown(String.raw`$\htmlClass{evil}{x}$ $\badcommand$

$$
\def\loop{\loop}\loop
$$`)
    expect(result.renderedHtml).toContain('katex-error')
    expect(result.renderedHtml).toContain('title="公式解析失败"')
    expect(result.renderedHtml).not.toContain('ParseError')
    expect(result.renderedHtml).toContain(String.raw`\htmlClass{evil}{x}`)
    expect(result.renderedHtml).toContain(String.raw`\badcommand`)
    expect(result.renderedHtml).toContain(String.raw`\def\loop`)
    expect(result.renderedHtml).not.toContain('class="evil"')
    expect(() => renderPublicArticleMarkdown(String.raw`$\htmlStyle{color:red}{x}$`)).not.toThrow()
  })

  it('renders duplicate footnote references with keyboard-reachable back links', () => {
    const result = renderPublicArticleMarkdown('A[^source] and again[^source]. Inline^[Inline text].\n\n[^source]: Footnote text.')
    expect(result.renderedHtml).toContain('class="footnote-ref"')
    expect(result.renderedHtml).toContain('href="#footnote1"')
    expect(result.renderedHtml).toContain('href="#footnote-ref1"')
    expect(result.renderedHtml).toContain('href="#footnote-ref1:1"')
    expect(result.renderedHtml).toContain('class="footnote-backref"')
    expect(result.renderedHtml).toContain('Inline text')
  })

  it('renders nested readonly task lists and all approved callouts only', () => {
    const result = renderPublicArticleMarkdown(`- [x] done\n  - [ ] nested\n\n::: note Custom note\nNote body\n:::\n\n::: tip\nTip body\n:::\n\n::: warning\nWarning body\n:::\n\n::: danger\nDanger body\n:::\n\n::: nope\nShould stay plain\n:::`)
    expect(result.renderedHtml).toContain('class="task-list-container"')
    expect(result.renderedHtml).toContain('class="task-list-item-checkbox"')
    expect(result.renderedHtml).toContain('disabled="disabled"')
    expect(result.renderedHtml).toContain('checked="checked"')
    expect(result.renderedHtml).toContain('class="markdown-callout markdown-callout--note" role="note"')
    expect(result.renderedHtml).toContain('注记：Custom note')
    expect(result.renderedHtml).toContain('markdown-callout--tip')
    expect(result.renderedHtml).toContain('markdown-callout--warning" role="alert"')
    expect(result.renderedHtml).toContain('markdown-callout--danger" role="alert"')
    expect(result.renderedHtml).toContain('::: nope')
    expect(result.renderedHtml).not.toContain('markdown-callout--nope')
  })

  it('wraps tables in a keyboard-focusable horizontal scroll region', () => {
    const result = renderPublicArticleMarkdown('| A | B |\n|---|---|\n| wide | value |')
    expect(result.renderedHtml).toContain('<div class="markdown-table-scroll" tabindex="0" role="region"')
    expect(result.renderedHtml).toContain('<table>')
    expect(result.renderedHtml).toContain('</table>\n</div>')
  })

  it('does not widen the XSS whitelist for raw HTML or unsafe protocols', () => {
    const result = renderPublicArticleMarkdown('<svg onload="alert(1)"><script>alert(2)</script></svg>\n\n[bad](javascript:alert(3))')
    expect(result.renderedHtml).not.toContain('<svg ')
    expect(result.renderedHtml).not.toContain('<script')
    expect(result.renderedHtml).toContain('&lt;svg')
    expect(result.renderedHtml).not.toContain('href="javascript:')
  })

  it('highlights every supported language through the server singleton', () => {
    const samples: Record<string, string> = {
      plaintext: 'plain <text>', bash: 'echo "$PATH"', shell: 'printf ok', powershell: 'Get-ChildItem',
      java: 'class Demo {}', kotlin: 'fun main() {}', xml: '<note>ok</note>', html: '<div>ok</div>',
      css: '.note { color: red; }', javascript: 'const answer = 42', typescript: 'const answer: number = 42',
      json: '{"ok":true}', yaml: 'ok: true', sql: 'SELECT 1;', vue: '<template><div /></template>',
      markdown: '# nested title', dockerfile: 'FROM node:24',
    }
    for (const [language, source] of Object.entries(samples)) {
      const result = renderPublicArticleMarkdown(`\`\`\`${language}\n${source}\n\`\`\``)
      expect(result.renderedHtml, language).toContain(`data-language="${language === 'shell' ? 'shellscript' : language}"`)
      expect(result.renderedHtml, language).toContain('data-line="1"')
      if (language !== 'plaintext') expect(result.renderedHtml, language).toContain('shiki-themes')
    }
  })

  it('escapes unknown languages and code metadata without throwing', () => {
    const result = renderPublicArticleMarkdown('```unknown [<script>.ts] {1,3-5} {0,2-1,999999999999} ignored\n<unsafe>&\nsecond\n```')
    expect(result.renderedHtml).toContain('code-highlight-fallback')
    expect(result.renderedHtml).toContain('&lt;unsafe&gt;&amp;')
    expect(result.renderedHtml).toContain('data-filename="&lt;script&gt;.ts"')
    expect(result.renderedHtml).toContain('aria-label="复制文件 &lt;script&gt;.ts"')
    expect(result.renderedHtml).toContain('class="line is-focused" data-line="1"')
    expect(result.renderedHtml).toContain('class="line" data-line="2"')
    expect(result.renderedHtml).not.toContain('ignored')
    expect(result.renderedHtml).not.toContain('<script>')
  })

  it('keeps long and empty code blocks readable', () => {
    const longCode = Array.from({ length: 2000 }, (_, index) => `const line${index + 1} = ${index + 1}`).join('\n')
    expect(() => renderPublicArticleMarkdown(`\`\`\`typescript\n${longCode}\n\`\`\``)).not.toThrow()
    expect(renderPublicArticleMarkdown(`\`\`\`typescript\n${longCode}\n\`\`\``).renderedHtml).toContain('data-line="2000"')
    expect(renderPublicArticleMarkdown('```javascript\n```').renderedHtml).toContain('data-line="1"')
  })

  it('forwards backend 404, cache-control, ETag, and conditional requests', async () => {
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      expect(new Headers(init?.headers).get('if-none-match')).toBe('"v1"')
      return new Response(JSON.stringify(article), { status: 200, headers: { etag: '"v1"', 'cache-control': 'public, max-age=0, s-maxage=60, must-revalidate' } })
    })
    const result = await fetchPublicArticleContent('security', 'http://api:8080', '"v1"', fetcher)
    expect(result.status).toBe(200)
    expect(result.headers.get('etag')).toBe('"v1"')
    expect(result.headers.get('cache-control')).toContain('s-maxage=60')
    expect(result.body?.article).toEqual(article)

    const notModified = await fetchPublicArticleContent('security', 'http://api:8080', '"v1"', vi.fn(async () => new Response(null, { status: 304, headers: { etag: '"v1"', 'cache-control': 'public, max-age=0, s-maxage=60, must-revalidate' } })))
    expect(notModified.status).toBe(304)
    expect(notModified.body).toBeUndefined()

    const missing = await fetchPublicArticleContent('missing', 'http://api:8080', undefined, vi.fn(async () => new Response(null, { status: 404 })))
    expect(missing.status).toBe(404)
  })
})
