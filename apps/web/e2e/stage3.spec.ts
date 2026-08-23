import { expect, test, type Page } from '@playwright/test'

const articlePath = '/articles/s3-08-advanced-markdown'
const publicPaths = ['/', '/articles', articlePath, '/garden', '/tools', '/about']
const viewports = [360, 768, 1280, 1600]
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

async function ensureSecondArticlePage(page: Page) {
  const current = await page.request.get('/api/v1/public/articles?page=0&size=1')
  expect(current.ok()).toBe(true)
  const { total } = await current.json() as { total: number }
  const missing = Math.max(0, 21 - total)
  if (!missing) return

  const anonymousCsrf = await page.request.get('/api/v1/admin/csrf')
  const anonymousToken = (await anonymousCsrf.json() as { token: string }).token
  const login = await page.request.post('/api/v1/admin/session', {
    headers: { 'X-CSRF-TOKEN': anonymousToken },
    data: { username: 'admin', password: 'password' },
  })
  expect(login.ok()).toBe(true)
  const authenticatedCsrf = await page.request.get('/api/v1/admin/csrf')
  const token = (await authenticatedCsrf.json() as { token: string }).token
  const batch = Date.now()

  for (let index = 0; index < missing; index += 1) {
    const slug = `s3-pagination-${batch}-${index}`
    const created = await page.request.post('/api/v1/admin/articles', {
      headers: { 'X-CSRF-TOKEN': token },
      data: {
        slug,
        title: `阶段三分页验收 ${index + 1}`,
        excerpt: '仅用于生产 Compose 的分页验收。',
        markdown: `# 阶段三分页验收 ${index + 1}\n\n分页信号可读。`,
      },
    })
    expect(created.status()).toBe(201)
    const article = await created.json() as { id: string; version: number }
    const published = await page.request.post(`/api/v1/admin/articles/${article.id}/publish`, {
      headers: { 'X-CSRF-TOKEN': token },
      data: { version: article.version },
    })
    expect(published.ok()).toBe(true)
  }
}

test.describe.serial('S3-08 public reading acceptance', () => {
  test('serves the SSR homepage and real article pagination', async ({ page }) => {
    const homepage = await page.request.get('/')
    expect(homepage.status()).toBe(200)
    const homepageHtml = await homepage.text()
    expect(homepageHtml).toContain('OBSERVATION / INITIAL FRAME')
    expect(homepageHtml).toContain('<h1 id="site-title">HaoBlog</h1>')
    expect(homepageHtml).toContain('class="recent-list"')

    const canonicalPageOne = await page.request.get('/articles?page=1', { maxRedirects: 0 })
    expect(canonicalPageOne.status()).toBe(301)
    expect(canonicalPageOne.headers().location).toBe('/articles')

    await ensureSecondArticlePage(page)
    await page.goto('/articles')
    await expect(page.getByRole('navigation', { name: '文章列表分页' }).getByRole('link', { name: '下一页' })).toBeVisible()
    await page.getByRole('navigation', { name: '文章列表分页' }).getByRole('link', { name: '下一页' }).click()
    await expect(page).toHaveURL(/\/articles\?page=2$/)
    await expect(page.getByRole('navigation', { name: '文章列表分页' }).locator('span[aria-current="page"]')).toHaveText('第 2 页')
    await expect(page.getByRole('navigation', { name: '文章列表分页' }).getByRole('link', { name: '上一页' })).toHaveAttribute('href', '/articles')

    const outOfRange = await page.goto('/articles?page=99999')
    expect(outOfRange?.status()).toBe(404)
  })

  test('verifies article, TOC, copy, SEO, JSON-LD and HTTPS images', async ({ page }) => {
    const imageRequests: string[] = []
    await page.route('https://cdn.example.test/**', route => {
      imageRequests.push(route.request().url())
      return route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
    })
    await page.goto(articlePath)

    await expect(page.locator('#article-title')).toHaveText('S3-08 高级 Markdown 固定验收文章')
    await expect(page.locator('.safe-markdown')).toContainText('正文、公式和代码在 SSR HTML 中直接可读。')
    await expect(page.locator('.article-toc a[href="#重复标题"]')).toBeVisible()
    await expect(page.locator('.article-toc a[href="#重复标题-2"]')).toBeVisible()
    await expect(page.locator('.safe-markdown .markdown-callout')).toHaveCount(4)
    await expect(page.locator('[data-language="java"][data-filename="SignalScore.java"]')).toBeVisible()
    await expect(page.locator('[data-language="powershell"][data-filename="verify.ps1"]')).toBeVisible()
    await expect(page.locator('.safe-markdown script')).toHaveCount(0)
    await expect(page.locator('.safe-markdown a[href^="javascript:"]')).toHaveCount(0)
    await expect(page.locator('.safe-markdown img[src^="http:"]')).toHaveCount(0)
    await expect(page.locator('.safe-markdown')).toContainText("alert('raw-html-blocked')")

    const siteResponse = await page.request.get('/api/v1/public/site')
    const site = await siteResponse.json() as { siteUrl: string; authorName: string }
    const canonical = new URL(articlePath, `${site.siteUrl}/`).toString()
    await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', /阶段三固定验收文章/)
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', canonical)
    await expect(page.locator('meta[property="og:title"]')).toHaveAttribute('content', 'S3-08 高级 Markdown 固定验收文章')
    const jsonLd = JSON.parse(await page.locator('script[type="application/ld+json"]').textContent() || '{}')
    expect(jsonLd).toMatchObject({
      '@type': 'BlogPosting',
      headline: 'S3-08 高级 Markdown 固定验收文章',
      url: canonical,
      author: { name: site.authorName },
      inLanguage: 'zh-CN',
    })

    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(page.url()).origin })
    const copyButton = page.locator('[data-language="java"] [data-code-copy]')
    await copyButton.click()
    await expect(copyButton).toHaveText('已复制')
    expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('public final class SignalScore')

    const image = page.locator('.safe-markdown img[src="https://cdn.example.test/observation-cover.png"]')
    await expect(image).toHaveAttribute('loading', 'lazy')
    await expect(image).toHaveAttribute('decoding', 'async')
    await image.scrollIntoViewIfNeeded()
    await expect(image).toBeVisible()
    await expect.poll(() => imageRequests).toContain('https://cdn.example.test/observation-cover.png')
  })

  test('verifies RSS, Sitemap and public 404 boundaries', async ({ page }) => {
    for (const [path, contentType, root] of [
      ['/rss.xml', 'application/rss+xml', '<rss'],
      ['/sitemap.xml', 'application/xml', '<urlset'],
    ] as const) {
      const response = await page.request.get(path)
      expect(response.status()).toBe(200)
      expect(response.headers()['content-type']).toContain(contentType)
      const xml = await response.text()
      expect(xml).toContain(root)
      if (path === '/rss.xml') {
        expect(xml).toContain('<item>')
      } else {
        expect(xml).toContain('s3-08-advanced-markdown')
        expect(xml).toContain('/tools')
      }
      const etag = response.headers().etag
      expect(etag).toBeTruthy()
      const cached = await page.request.get(path, { headers: { 'If-None-Match': etag } })
      expect(cached.status()).toBe(304)
      expect(await cached.text()).toBe('')
    }

    for (const path of ['/articles/not-published', '/missing-stage-three']) {
      const response = await page.goto(path)
      expect(response?.status()).toBe(404)
      await expect(page.getByRole('heading', { name: '页面未找到' })).toBeVisible()
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex,nofollow')
    }
  })

  test('supports no JavaScript, Save-Data and reduced-motion', async ({ browser }) => {
    const baseURL = process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1'
    const noJs = await browser.newContext({ baseURL, javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    const noJsResponse = await noJsPage.goto(articlePath)
    expect(noJsResponse?.status()).toBe(200)
    await expect(noJsPage.locator('#article-title')).toBeVisible()
    await expect(noJsPage.locator('.article-toc a[href="#重复标题-2"]')).toBeVisible()
    await expect(noJsPage.locator('.safe-markdown .katex').first()).toBeVisible()
    await expect(noJsPage.locator('.safe-markdown .shiki').first()).toBeVisible()
    await expect(noJsPage.locator('.safe-markdown .mermaid-source')).toContainText('flowchart TD')
    await noJs.close()

    const saveData = await browser.newContext({ baseURL, extraHTTPHeaders: { 'Save-Data': 'on' } })
    const saveDataPage = await saveData.newPage()
    const imageRequests: string[] = []
    saveDataPage.on('request', request => { if (request.resourceType() === 'image') imageRequests.push(request.url()) })
    await saveDataPage.goto(articlePath)
    await expect(saveDataPage.locator('html')).toHaveAttribute('data-save-data', 'on')
    await expect(saveDataPage.locator('.markdown-image-placeholder')).toContainText('观测站静态封面')
    await expect(saveDataPage.locator('[data-mermaid-render]')).toBeVisible()
    expect(imageRequests).toEqual([])
    await saveData.close()

    const reduced = await browser.newContext({ baseURL })
    const reducedPage = await reduced.newPage()
    await reducedPage.addInitScript(() => {
      const observed: string[] = []
      Object.defineProperty(window, '__haoblogObservedHeadings', { value: observed })
      const observe = IntersectionObserver.prototype.observe
      IntersectionObserver.prototype.observe = function (target) {
        if (target instanceof HTMLElement && target.matches('h2, h3')) observed.push(target.id)
        return observe.call(this, target)
      }
    })
    await reducedPage.emulateMedia({ reducedMotion: 'reduce' })
    await reducedPage.goto(articlePath)
    expect(await reducedPage.locator('.article-toc a').count()).toBeGreaterThan(0)
    expect(await reducedPage.evaluate(() => (window as Window & { __haoblogObservedHeadings?: string[] }).__haoblogObservedHeadings)).toEqual([])
    await reduced.close()
  })

  test('keeps public pages responsive and keyboard controls visibly focused', async ({ page }) => {
    for (const width of viewports) {
      await page.setViewportSize({ width, height: 900 })
      for (const path of publicPaths) {
        await page.goto(path)
        await expect(page.locator('main')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth), `${path} at ${width}px`).toBeLessThanOrEqual(width)
      }
    }

    await page.setViewportSize({ width: 1280, height: 900 })
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto(articlePath)
    const controls = page.locator('a, button, summary, pre[tabindex="0"], [role="region"][tabindex="0"]')
    const count = await controls.count()
    expect(count).toBeGreaterThan(0)
    for (let index = 0; index < count; index += 1) {
      const control = controls.nth(index)
      if (!(await control.isVisible()) || !(await control.isEnabled().catch(() => true))) continue
      await control.focus()
      const focusState = await control.evaluate(element => {
        const style = getComputedStyle(element)
        return {
          visible: style.outlineStyle !== 'none' && Number.parseFloat(style.outlineWidth) > 0,
          label: `${element.tagName}.${element.className}`,
          outline: `${style.outlineStyle} ${style.outlineWidth}`,
        }
      })
      expect(focusState.visible, `${focusState.label} outline=${focusState.outline}`).toBe(true)
    }
  })
})
