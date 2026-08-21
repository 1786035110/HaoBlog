import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { describe, expect, it } from 'vitest'
import PublicArticleList from '../app/components/articles/PublicArticleList.vue'
import PublicArticleBody from '../app/components/articles/PublicArticleBody.vue'
import PublicHomeOverview from '../app/components/home/PublicHomeOverview.vue'
import { isPublicArticlePageOutOfRange, parsePublicArticlePage, publicArticlePageUrl } from '../app/utils/publicArticlePagination'
import { buildPublicArticleSeo } from '../app/utils/publicArticleSeo'
import { buildPublicArticleContent } from '../server/utils/publicArticleContent'
import type { components } from '@haoblog/api-client'

type Article = components['schemas']['ArticleResponse']
type ArticleList = components['schemas']['ArticleListResponse']

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

const nuxtLinkStub = {
  props: { to: { type: String, required: true } },
  setup(props: { to: string }, { slots }: { slots: { default?: () => unknown } }) {
    return () => h('a', { href: props.to }, slots.default?.())
  },
}

async function renderPublic(component: Parameters<typeof createSSRApp>[0], props: Record<string, unknown>) {
  const app = createSSRApp(component, props)
  app.component('NuxtLink', nuxtLinkStub)
  return renderToString(app)
}

describe('public article SSR contract', () => {
  it('renders homepage site information and recent article links into SSR HTML', async () => {
    const recent: ArticleList = {
      items: [{ id: 'article-1', slug: 'ssr-signal', title: 'Published title', excerpt: 'Published excerpt', publishedAt: article.publishedAt, coverImageUrl: null }],
      page: 0,
      size: 5,
      total: 1,
    }
    const html = await renderPublic(PublicHomeOverview, {
      site: { title: 'HaoBlog 站点', description: '夜间校准开发者信号', siteUrl: 'https://blog.example.test', authorName: 'Hao' },
      recent,
    })
    expect(html).toContain('HaoBlog 站点')
    expect(html).toContain('夜间校准开发者信号')
    expect(html).toContain('href="/articles/ssr-signal"')
    expect(html).toContain('Published title')
  })

  it('renders empty, single-page and last-page list states with semantic navigation', async () => {
    const empty: ArticleList = { items: [], page: 0, size: 20, total: 0 }
    expect(await renderPublic(PublicArticleList, { result: empty, page: 1 })).toContain('当前没有已锁定的公开文章。')

    const single: ArticleList = { items: [{ id: 'article-1', slug: 'ssr-signal', title: 'Published title', excerpt: 'Published excerpt', publishedAt: article.publishedAt, coverImageUrl: null }], page: 0, size: 20, total: 1 }
    const singleHtml = await renderPublic(PublicArticleList, { result: single, page: 1 })
    expect(singleHtml).toContain('2026.01.01')
    expect(singleHtml).not.toContain('文章列表分页')

    const last: ArticleList = { ...single, page: 1, total: 21 }
    const lastHtml = await renderPublic(PublicArticleList, { result: last, page: 2 })
    expect(lastHtml).toContain('上一页')
    expect(lastHtml).toContain('href="/articles"')
    expect(lastHtml).not.toContain('下一页')
  })

  it('renders HTTPS covers with stable layout attributes and omits missing or unsafe covers', async () => {
    const result: ArticleList = {
      items: [
        { id: 'article-1', slug: 'with-cover', title: 'With cover', excerpt: null, publishedAt: article.publishedAt, coverImageUrl: 'https://cdn.example.test/cover.webp' },
        { id: 'article-2', slug: 'without-cover', title: 'Without cover', excerpt: null, publishedAt: article.publishedAt, coverImageUrl: null },
        { id: 'article-3', slug: 'unsafe-cover', title: 'Unsafe cover', excerpt: null, publishedAt: article.publishedAt, coverImageUrl: 'http://cdn.example.test/cover.webp' },
      ],
      page: 0,
      size: 20,
      total: 3,
    }
    const html = await renderPublic(PublicArticleList, { result, page: 1 })
    expect(html).toContain('src="https://cdn.example.test/cover.webp"')
    expect(html).toContain('loading="lazy"')
    expect(html).toContain('decoding="async"')
    expect(html).toContain('article-cover-frame')
    expect(html.match(/article-cover-frame/g)).toHaveLength(1)
  })

  it('keeps frontend page numbers 1-based and rejects invalid or out-of-range pages', () => {
    expect(parsePublicArticlePage(undefined)).toEqual({ page: 1, canonical: false })
    expect(parsePublicArticlePage('1')).toEqual({ page: 1, canonical: true })
    expect(parsePublicArticlePage('3')).toEqual({ page: 3, canonical: false })
    expect(parsePublicArticlePage('0')).toBeNull()
    expect(parsePublicArticlePage('1.5')).toBeNull()
    expect(parsePublicArticlePage('not-a-page')).toBeNull()
    expect(parsePublicArticlePage(['2'])).toBeNull()
    expect(publicArticlePageUrl(1)).toBe('/articles')
    expect(publicArticlePageUrl(3)).toBe('/articles?page=3')
    expect(isPublicArticlePageOutOfRange(1, { items: [], page: 0, size: 20, total: 0 })).toBe(false)
    expect(isPublicArticlePageOutOfRange(2, { items: [], page: 1, size: 20, total: 20 })).toBe(true)
    expect(isPublicArticlePageOutOfRange(2, { items: [singleSummary()], page: 1, size: 20, total: 21 })).toBe(false)
  })

  it('renders title, excerpt and safe markdown into SSR HTML', async () => {
    const content = buildPublicArticleContent(article)
    const html = await renderToString(createSSRApp(PublicArticleBody, { content }))
    expect(html).toContain('Published title')
    expect(html).toContain('Published excerpt')
    expect(html).toContain('Published body')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('keeps highlighted code, line metadata, and copy affordance in SSR HTML', async () => {
    const content = buildPublicArticleContent({ ...article, markdown: '# Readable\n\n```typescript [answer.ts] {2}\nconst answer: number = 41\nconst next = answer + 1\n```' })
    const html = await renderToString(createSSRApp(PublicArticleBody, { content }))
    expect(html).toContain('class="shiki shiki-themes')
    expect(html).toContain('data-filename="answer.ts"')
    expect(html).toContain('data-line="2"')
    expect(html).toContain('class="code-copy-button"')
    expect(html).toContain('aria-label="复制文件 answer.ts"')
    expect(html).toContain('const')
    expect(html).toContain('next')
  })

  it('keeps Mermaid figure, caption, and escaped source in SSR HTML', async () => {
    const content = buildPublicArticleContent({ ...article, markdown: '```mermaid\nflowchart TD\nA[<script>]-->B\n```' })
    const html = await renderToString(createSSRApp(PublicArticleBody, { content }))
    expect(html).toContain('class="mermaid-figure"')
    expect(html).toContain('<figcaption class="mermaid-caption">MERMAID / DIAGRAM')
    expect(html).toContain('<pre class="mermaid-source"')
    expect(html).toContain('A[&lt;script&gt;]--&gt;B')
    expect(html).not.toContain('<script>')
  })

  it('hydrates the same structure without Vue warnings', async () => {
    const content = buildPublicArticleContent(article)
    const serverApp = createSSRApp(PublicArticleBody, { content })
    const html = await renderToString(serverApp)
    document.body.innerHTML = `<div id="app">${html}</div>`
    const warnings: string[] = []
    const clientApp = createSSRApp(PublicArticleBody, { content })
    clientApp.config.warnHandler = message => warnings.push(message)
    clientApp.mount('#app')
    expect(warnings).toEqual([])
    expect(document.querySelector('#article-title')?.textContent).toBe('Published title')
    expect(document.querySelector('.safe-markdown h2')?.textContent).toBe('Published body')
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

function singleSummary(): components['schemas']['ArticleSummary'] {
  return { id: 'article-1', slug: 'ssr-signal', title: 'Published title', excerpt: 'Published excerpt', publishedAt: article.publishedAt, coverImageUrl: null }
}
