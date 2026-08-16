import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import PublicArticleBody from '../app/components/articles/PublicArticleBody.vue'
import { buildPublicArticleSeo } from '../app/utils/publicArticleSeo'
import type { components } from '@haoblog/api-client'

type Article = components['schemas']['ArticleResponse']

const article: Article = {
  id: 'article-1',
  slug: 'ssr-signal',
  title: 'Published title',
  excerpt: 'Published excerpt',
  publishedAt: '2026-01-01T00:00:00Z',
  markdown: '# Published body\n\n<script>alert(1)</script>\n\n[docs](https://example.com)',
  seoTitle: 'SEO title',
  seoDescription: 'SEO description',
  coverImageUrl: 'https://cdn.example.test/cover.png',
}

describe('public article SSR contract', () => {
  it('renders title, excerpt and safe markdown into SSR HTML', async () => {
    const html = await renderToString(createSSRApp(PublicArticleBody, { article }))
    expect(html).toContain('Published title')
    expect(html).toContain('Published excerpt')
    expect(html).toContain('Published body')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('hydrates the same structure without Vue warnings', async () => {
    const serverApp = createSSRApp(PublicArticleBody, { article })
    const html = await renderToString(serverApp)
    document.body.innerHTML = `<div id="app">${html}</div>`
    const warnings: string[] = []
    const clientApp = createSSRApp(PublicArticleBody, { article })
    clientApp.config.warnHandler = message => warnings.push(message)
    clientApp.mount('#app')
    expect(warnings).toEqual([])
    expect(document.querySelector('#article-title')?.textContent).toBe('Published title')
    expect(document.querySelector('.safe-markdown h1')?.textContent).toBe('Published body')
  })

  it('applies SEO fallback order and rejects unsafe image protocols', () => {
    expect(buildPublicArticleSeo(article, 'https://blog.example.test')).toEqual({
      title: 'SEO title',
      description: 'SEO description',
      image: 'https://cdn.example.test/cover.png',
    })
    expect(buildPublicArticleSeo({ ...article, seoTitle: null, seoDescription: null, coverImageUrl: 'javascript:alert(1)' }, 'https://blog.example.test')).toEqual({
      title: 'Published title',
      description: 'Published excerpt',
      image: 'https://blog.example.test/og-default.svg',
    })
  })
})
