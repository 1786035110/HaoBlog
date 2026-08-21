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
