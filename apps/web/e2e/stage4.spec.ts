import { expect, test } from '@playwright/test'

test.describe('S4-03 article comment signal', () => {
  test('keeps the comment signal SSR-readable and submits through the lazy form context', async ({ page, browser }) => {
    const articles = await page.request.get('/api/v1/public/articles?size=1')
    expect(articles.ok()).toBe(true)
    const firstArticle = (await articles.json() as { items: Array<{ slug: string }> }).items[0]
    expect(firstArticle?.slug).toBeTruthy()
    const articlePath = `/articles/${firstArticle.slug}`

    const noJs = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    const noJsResponse = await noJsPage.goto(articlePath)
    expect(noJsResponse?.status()).toBe(200)
    await expect(noJsPage.locator('.comment-signal')).toBeVisible()
    await expect(noJsPage.locator('.comment-signal')).toContainText(/回波信号|评论信号已关闭/)
    await noJs.close()

    let submitHeaders: Record<string, string> | undefined
    await page.route('**/api/v1/public/articles/*/comments/form-context', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ csrfToken: 'e2e-csrf', challenge: 'e2e-challenge', expiresAt: new Date(Date.now() + 60_000).toISOString(), commentsEnabled: true }),
    }))
    await page.route('**/api/v1/public/articles/*/comments', async route => {
      if (route.request().method() !== 'POST') return route.continue()
      submitHeaders = route.request().headers()
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({ id: '00000000-0000-7000-8000-000000000099', status: 'PENDING', createdAt: new Date().toISOString(), deleteToken: 'e2e-delete-token' }),
      })
    })
    await page.goto(articlePath)
    await expect(page.getByRole('button', { name: '展开评论入口' })).toBeVisible()
    await page.getByRole('button', { name: '展开评论入口' }).click()
    await expect(page.getByLabel('昵称')).toBeVisible()
    await page.getByLabel('昵称').fill('Playwright 观测员')
    await page.getByLabel('正文').fill('这是一条待审核回波。')
    await page.waitForTimeout(3200)
    await page.getByRole('button', { name: '发送回波' }).click()
    await expect(page.locator('[data-status="pending"]')).toContainText('待审核')
    expect(submitHeaders?.['x-csrf-token']).toBe('e2e-csrf')
    expect(await page.evaluate(() => localStorage.getItem('haoblog-comment-delete:00000000-0000-7000-8000-000000000099'))).toBe('e2e-delete-token')
  })
})

test.describe('S4-07 public toolbox switchboard', () => {
  test('renders the full directory in SSR and keeps interaction client-owned', async ({ page, browser }) => {
    const batch = Date.now()
    const anonymousCsrf = await page.request.get('/api/v1/admin/csrf')
    const anonymousToken = (await anonymousCsrf.json() as { token: string }).token
    const login = await page.request.post('/api/v1/admin/session', {
      headers: { 'X-CSRF-TOKEN': anonymousToken },
      data: { username: 'admin', password: 'password' },
    })
    expect(login.ok()).toBe(true)
    const authenticatedCsrf = await page.request.get('/api/v1/admin/csrf')
    const token = (await authenticatedCsrf.json() as { token: string }).token
    const category = await page.request.post('/api/v1/admin/tool-categories', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { name: `S4-07 ${batch}`, slug: `s4-07-${batch}`, description: '公共工具验收分类' },
    })
    expect(category.status()).toBe(201)
    const categoryId = (await category.json() as { id: string }).id
    const createTool = (body: Record<string, unknown>) => page.request.post('/api/v1/admin/tools', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { categoryId, status: 'ACTIVE', tags: [], ...body },
    })
    expect((await createTool({ type: 'EMBEDDED', componentKey: 'json-format', title: `JSON Formatter ${batch}`, slug: `json-format-${batch}`, description: 'JSON 工程格式化入口', tags: ['json', 'format'] })).status()).toBe(201)
    expect((await createTool({ type: 'LINK', url: 'https://example.com/toolbox', title: `Link Tool ${batch}`, slug: `link-tool-${batch}`, description: '安全外链入口', tags: ['link'] })).status()).toBe(201)

    const api = await page.request.get('/api/v1/public/tools')
    expect(api.ok()).toBe(true)
    const payload = await api.json() as { items: Array<{ title: string; type: string }>; categories: Array<{ slug: string }> }
    expect(payload.items.some(item => item.title.includes(`JSON Formatter ${batch}`))).toBe(true)
    expect(payload.categories.some(item => item.slug === `s4-07-${batch}`)).toBe(true)
    expect((await page.request.get('/api/v1/public/tools?type=EMBEDDED&keyword=json')).ok()).toBe(true)

    const ssr = await page.request.get('/tools')
    const html = await ssr.text()
    expect(ssr.status()).toBe(200)
    expect(html).toContain(`JSON Formatter ${batch}`)
    expect(html).toContain('JSON 工程格式化入口')
    expect(html).toContain('#json')
    expect(html).not.toContain('noindex')

    const warnings: string[] = []
    page.on('console', message => { if (message.type() === 'warning') warnings.push(message.text()) })
    await page.goto('/tools')
    await expect(page.getByRole('heading', { name: '工具目录' })).toBeVisible()
    expect(warnings.filter(message => /hydration|mismatch/i.test(message))).toEqual([])

    const embedded = page.locator('.tool-entry').filter({ hasText: `JSON Formatter ${batch}` })
    await embedded.getByRole('button', { name: `收藏 JSON Formatter ${batch}` }).click()
    expect(await page.evaluate(() => localStorage.getItem('haoblog-tool-favorites'))).toContain('[')
    await embedded.getByRole('button', { name: 'UNFOLD' }).click()
    await expect(embedded.locator('.embedded-slot')).toContainText('内嵌组件槽位已建立')
    await embedded.getByRole('button', { name: 'CLOSE' }).click()
    await embedded.getByRole('button', { name: 'UNFOLD' }).click()
    await embedded.getByRole('button', { name: 'CLOSE' }).click()
    await page.getByRole('button', { name: '最常用' }).click()
    await expect(page.locator('.tool-entry').first()).toContainText(`JSON Formatter ${batch}`)

    const linked = page.locator('.tool-entry').filter({ hasText: `Link Tool ${batch}` })
    await linked.getByRole('button', { name: 'UNFOLD' }).click()
    await expect(linked.locator('a[rel="noopener noreferrer external"]')).toHaveAttribute('href', 'https://example.com/toolbox')

    const reduced = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' })
    const reducedPage = await reduced.newPage()
    await reducedPage.emulateMedia({ reducedMotion: 'reduce' })
    await reducedPage.goto('/tools')
    expect(await reducedPage.locator('.tools-scene').evaluate(element => getComputedStyle(element).animationName)).toBe('none')
    await reduced.close()

    const saveData = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', extraHTTPHeaders: { 'Save-Data': 'on' } })
    const saveDataPage = await saveData.newPage()
    await saveDataPage.goto('/tools')
    await expect(saveDataPage.locator('.embedded-slot')).toHaveCount(0)
    await saveData.close()

    const touch = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', hasTouch: true, isMobile: true })
    const touchPage = await touch.newPage()
    await touchPage.goto('/tools')
    await touchPage.locator('.tool-entry').filter({ hasText: `JSON Formatter ${batch}` }).getByRole('button', { name: 'UNFOLD' }).click()
    await expect(touchPage.locator('.embedded-slot')).toBeVisible()
    await touch.close()
  })
})

test.describe('S4-08 embedded browser tools', () => {
  test('runs all five tools locally, keeps inputs out of the network, and stays usable at 360px', async ({ page }) => {
    const response = await page.request.get('/api/v1/public/tools?type=EMBEDDED')
    expect(response.ok()).toBe(true)
    const payload = await response.json() as { items: Array<{ title: string; componentKey: string }> }
    const expected = ['json-format', 'base64', 'url-codec', 'timestamp', 'regex-test']
    expect(new Set(payload.items.map(item => item.componentKey))).toEqual(new Set(expected))
    const seededTitles: Record<string, string> = {
      'json-format': 'JSON 格式化',
      base64: 'Base64 编解码',
      'url-codec': 'URL 编解码',
      timestamp: '时间戳转换',
      'regex-test': '正则测试',
    }

    await page.setViewportSize({ width: 360, height: 900 })
    await page.goto('/tools')
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
    const requests: string[] = []
    page.on('request', request => requests.push(request.url()))

    const entryFor = (key: string) => page.locator('.tool-entry').filter({ hasText: seededTitles[key] })
    for (const key of expected) {
      const entry = entryFor(key)
      await entry.getByRole('button', { name: 'UNFOLD' }).click()
      await expect(entry.locator(`[data-component-key="${key}"]`)).toBeVisible()
    }

    const json = entryFor('json-format')
    await json.getByLabel('JSON 输入').fill('{"ok":true}')
    await json.getByRole('button', { name: '格式化 / 两空格' }).click()
    await expect(json.locator('.tool-output')).toContainText('"ok": true')

    const base64 = entryFor('base64')
    await base64.getByLabel('输入').fill('中文')
    await base64.getByRole('button', { name: 'UTF-8 编码' }).click()
    await expect(base64.locator('.tool-output')).toHaveText('5Lit5paH')

    const url = entryFor('url-codec')
    await url.getByLabel('输入').fill('%E0%A4%A')
    await url.getByRole('button', { name: '解码', exact: true }).click()
    await expect(url).toContainText('非法百分号转义')

    const timestamp = entryFor('timestamp')
    await timestamp.getByLabel('秒 / 毫秒时间戳').fill('0')
    await timestamp.getByRole('button', { name: '转换时间戳' }).click()
    await expect(timestamp).toContainText('1970-01-01T00:00:00.000Z')

    const regex = entryFor('regex-test')
    await regex.getByLabel('pattern').fill('^')
    await regex.getByLabel('flags').fill('gm')
    await regex.getByLabel('测试文本').fill('a\nb')
    await regex.getByRole('button', { name: '运行正则' }).click()
    await expect(regex.getByRole('list', { name: '匹配列表' })).toContainText('零长度匹配')
    expect(requests.filter(url => /\/api\/v1\//.test(url))).toEqual([])

    await page.keyboard.press('Tab')
    await expect(page.locator(':focus')).toBeVisible()
  })
})
