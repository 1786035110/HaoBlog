import { expect, test, type APIRequestContext, type Browser } from '@playwright/test'

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
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ id: '00000000-0000-7000-8000-000000000099', status: 'APPROVED', createdAt: new Date().toISOString(), deleteToken: 'e2e-delete-token' }),
      })
    })
    await page.goto(articlePath)
    await expect(page.getByRole('button', { name: '展开评论入口' })).toBeVisible()
    await page.getByRole('button', { name: '展开评论入口' }).click()
    await expect(page.getByLabel('昵称')).toBeVisible()
    await page.getByLabel('昵称').fill('Playwright 观测员')
    await page.getByRole('textbox', { name: '正文' }).fill('这是一条即时发布的回波。')
    await page.waitForTimeout(3200)
    await page.getByRole('button', { name: '发送回波' }).click()
    await expect(page.locator('[data-status="published"]')).toContainText('评论已发布')
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
      data: { username: process.env.HAOBLOG_E2E_ADMIN_USERNAME || 'admin', password: process.env.HAOBLOG_E2E_ADMIN_PASSWORD || 'password' },
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

test.describe('S4-09 minimal article search', () => {
  test('renders normalized public search in SSR, supports no-JS, pagination links and 360px keyboard use', async ({ page, browser }) => {
    const articles = await page.request.get('/api/v1/public/articles?size=1')
    expect(articles.ok()).toBe(true)
    const first = (await articles.json() as { items: Array<{ slug: string; title: string }> }).items[0]
    expect(first?.slug).toBeTruthy()
    const query = Array.from(first.title).slice(0, 3).join('')
    expect(Array.from(query).length).toBeGreaterThanOrEqual(2)

    const api = await page.request.get('/api/v1/public/search/articles', { params: { q: query } })
    expect(api.ok()).toBe(true)
    const payload = await api.json() as { items: Array<{ slug: string; title: string }>; total: number }
    expect(payload.total).toBeGreaterThan(0)
    expect(payload.items.some(item => item.slug === first.slug)).toBe(true)

    const ssr = await page.request.get(`/search?q=${encodeURIComponent(query)}`)
    expect(ssr.status()).toBe(200)
    const html = await ssr.text()
    expect(html).toContain(first.title)
    expect(html).toContain('name="robots"')
    expect(html).toContain('noindex,follow')
    expect(html).toContain('name="q"')
    expect(html).not.toContain('/sitemap.xml/search')

    const noJs = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    await noJsPage.setViewportSize({ width: 360, height: 900 })
    const noJsResponse = await noJsPage.goto(`/search?q=${encodeURIComponent(query)}`)
    expect(noJsResponse?.status()).toBe(200)
    await expect(noJsPage.getByRole('heading', { name: '文章搜索' })).toBeVisible()
    await expect(noJsPage.locator('input[name="q"]')).toHaveValue(query)
    await expect(noJsPage.locator('a[href^="/articles/"]').first()).toBeVisible()
    expect(await noJsPage.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
    await noJs.close()

    await page.setViewportSize({ width: 360, height: 900 })
    await page.goto(`/search?q=${encodeURIComponent(query)}`)
    await page.getByLabel('检索词').focus()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'SEARCH →' })).toBeFocused()

    const sitemap = await page.request.get('/sitemap.xml')
    expect(await sitemap.text()).not.toContain('/search')
  })
})

type ArticleAdminResponse = { id: string; version: number; title: string; commentsEnabled: boolean }
type CommentSubmission = { id: string; status: string; deleteToken: string | null }
type CommentFormContext = { csrfToken: string; challenge: string; commentsEnabled: boolean }

const baseURL = () => process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1'

async function loginAcceptanceAdmin(request: APIRequestContext) {
  const anonymousCsrf = await request.get('/api/v1/admin/csrf')
  expect(anonymousCsrf.ok()).toBe(true)
  const anonymousToken = (await anonymousCsrf.json() as { token: string }).token
  const login = await request.post('/api/v1/admin/session', {
    headers: { 'X-CSRF-TOKEN': anonymousToken },
    data: { username: process.env.HAOBLOG_E2E_ADMIN_USERNAME || 'admin', password: process.env.HAOBLOG_E2E_ADMIN_PASSWORD || 'password' },
  })
  expect(login.ok()).toBe(true)
  const csrf = await request.get('/api/v1/admin/csrf')
  expect(csrf.ok()).toBe(true)
  return (await csrf.json() as { token: string }).token
}

async function createPublishedAcceptanceArticle(request: APIRequestContext, token: string, input: {
  slug: string
  title: string
  excerpt: string
  markdown: string
  commentsEnabled?: boolean
}) {
  const created = await request.post('/api/v1/admin/articles', {
    headers: { 'X-CSRF-TOKEN': token },
    data: { ...input, commentsEnabled: input.commentsEnabled ?? true },
  })
  expect(created.status()).toBe(201)
  const article = await created.json() as ArticleAdminResponse
  const published = await request.post(`/api/v1/admin/articles/${article.id}/publish`, {
    headers: { 'X-CSRF-TOKEN': token },
    data: { version: article.version },
  })
  expect(published.ok()).toBe(true)
  return article
}

async function getCommentForm(request: APIRequestContext, slug: string) {
  const response = await request.get(`/api/v1/public/articles/${encodeURIComponent(slug)}/comments/form-context`)
  expect(response.ok()).toBe(true)
  return await response.json() as CommentFormContext
}

async function submitAcceptanceComment(browser: Browser, slug: string, input: { nickname: string; content: string; email?: string; parentId?: string }) {
  const context = await browser.newContext({ baseURL: baseURL() })
  const request = context.request
  const form = await getCommentForm(request, slug)
  await new Promise(resolve => setTimeout(resolve, 3_200))
  const response = await request.post(`/api/v1/public/articles/${encodeURIComponent(slug)}/comments`, {
    headers: { 'X-CSRF-TOKEN': form.csrfToken },
    data: { ...input, challenge: form.challenge },
  })
  return { context, form, response, payload: await response.json() as CommentSubmission }
}

async function moderateAcceptanceComment(request: APIRequestContext, token: string, id: string, status: 'APPROVED' | 'SPAM' | 'REJECTED') {
  const detailResponse = await request.get(`/api/v1/admin/comments/${id}`)
  expect(detailResponse.ok()).toBe(true)
  const detail = await detailResponse.json() as { version: number }
  const response = await request.post(`/api/v1/admin/comments/${id}/moderation`, {
    headers: { 'X-CSRF-TOKEN': token },
    data: { version: detail.version, status, reason: 'S4-10 运行态验收' },
  })
  expect(response.ok()).toBe(true)
}

test.describe.serial('S4-10 full live contracts', () => {
  test('publishes comments immediately and keeps flags, security signals and Studio management', async ({ page, browser }) => {
    test.setTimeout(120_000)
    const batch = Date.now()
    const token = await loginAcceptanceAdmin(page.request)
    const openSlug = `s4-10-open-${batch}`
    const closedSlug = `s4-10-closed-${batch}`
    await createPublishedAcceptanceArticle(page.request, token, {
      slug: openSlug,
      title: `S4-10 开放评论文章 ${batch}`,
      excerpt: 'S4-10 摘要验收命中。',
      markdown: '# S4-10 开放评论文章\n\nS4-10 正文验收命中。',
    })
    await createPublishedAcceptanceArticle(page.request, token, {
      slug: closedSlug,
      title: `S4-10 关闭评论文章 ${batch}`,
      excerpt: '关闭评论文章摘要。',
      markdown: '# S4-10 关闭评论文章\n\n评论入口关闭。',
      commentsEnabled: false,
    })

    const published = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 即时发布 ${batch}`, content: '即时发布评论信号。', email: `s410-${batch}@example.test` })
    expect(published.response.status()).toBe(201)
    expect(published.payload.status).toBe('APPROVED')

    const replayContext = await browser.newContext({ baseURL: baseURL() })
    const replayForm = await getCommentForm(replayContext.request, openSlug)
    await new Promise(resolve => setTimeout(resolve, 3_200))
    const firstReplay = await replayContext.request.post(`/api/v1/public/articles/${openSlug}/comments`, {
      headers: { 'X-CSRF-TOKEN': replayForm.csrfToken },
      data: { nickname: `S4-10 重放 ${batch}`, content: '第一次挑战提交。', challenge: replayForm.challenge },
    })
    expect(firstReplay.status()).toBe(201)
    const replay = await replayContext.request.post(`/api/v1/public/articles/${openSlug}/comments`, {
      headers: { 'X-CSRF-TOKEN': replayForm.csrfToken },
      data: { nickname: `S4-10 重放 ${batch}`, content: '重复使用挑战。', challenge: replayForm.challenge },
    })
    expect(replay.status()).toBe(400)
    await replayContext.close()

    const maliciousContext = await browser.newContext({ baseURL: baseURL() })
    const maliciousForm = await getCommentForm(maliciousContext.request, openSlug)
    const malicious = await maliciousContext.request.post(`/api/v1/public/articles/${openSlug}/comments`, {
      headers: { 'X-CSRF-TOKEN': maliciousForm.csrfToken },
      data: { nickname: 'S4-10 安全', content: '<script>alert(1)</script>', challenge: maliciousForm.challenge },
    })
    expect(malicious.status()).toBe(400)
    await maliciousContext.close()

    const approved = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 通过 ${batch}`, content: '将被审核通过。' })
    expect(approved.response.status()).toBe(201)
    await page.goto('/studio/comments')
    await expect(page.getByRole('heading', { name: '评论管理' })).toBeVisible()
    await expect(page.getByRole('button', { name: new RegExp(`S4-10 即时发布 ${batch}`) })).toBeVisible()
    await page.getByRole('button', { name: new RegExp(`S4-10 即时发布 ${batch}`) }).click()
    await page.locator('#moderation-status').selectOption('SPAM')
    await page.getByRole('button', { name: '写入审核结果' }).click()
    await expect(page.locator('[data-status="SPAM"]').first()).toBeVisible()
    await moderateAcceptanceComment(page.request, token, published.payload.id, 'APPROVED')
    await moderateAcceptanceComment(page.request, token, approved.payload.id, 'APPROVED')

    const spam = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 垃圾 ${batch}`, content: '垃圾评论状态。' })
    const rejected = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 拒绝 ${batch}`, content: '拒绝评论状态。' })
    const reply = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 回复 ${batch}`, content: '一级回复评论状态。', parentId: published.payload.id })
    const deleted = await submitAcceptanceComment(browser, openSlug, { nickname: `S4-10 删除 ${batch}`, content: '用户将删除此评论。' })
    for (const result of [spam, rejected, reply, deleted]) expect(result.response.status()).toBe(201)
    await moderateAcceptanceComment(page.request, token, spam.payload.id, 'SPAM')
    await moderateAcceptanceComment(page.request, token, rejected.payload.id, 'REJECTED')
    await moderateAcceptanceComment(page.request, token, reply.payload.id, 'APPROVED')
    const deletedResponse = await deleted.context.request.delete(`/api/v1/public/comments/${deleted.payload.id}`, {
      headers: { 'X-CSRF-TOKEN': deleted.form.csrfToken, 'X-Comment-Delete-Token': deleted.payload.deleteToken || '' },
    })
    expect(deletedResponse.status()).toBe(204)
    await deleted.context.close()
    await published.context.close(); await approved.context.close(); await spam.context.close(); await rejected.context.close(); await reply.context.close()

    const publicComments = await page.request.get(`/api/v1/public/articles/${openSlug}/comments`)
    expect(publicComments.ok()).toBe(true)
    const publicPayload = await publicComments.json() as { items: Array<{ nickname: string; replies: Array<{ nickname: string }> }> }
    expect(publicPayload.items.some(item => item.nickname.includes(`S4-10 即时发布 ${batch}`))).toBe(true)
    expect(publicPayload.items.some(item => item.replies.some(replyItem => replyItem.nickname.includes(`S4-10 回复 ${batch}`)))).toBe(true)
    expect(JSON.stringify(publicPayload)).not.toContain(`S4-10 删除 ${batch}`)

    const closedForm = await getCommentForm(page.request, closedSlug)
    expect(closedForm.commentsEnabled).toBe(false)
    const site = await page.request.get('/api/v1/admin/site')
    const siteSettings = await site.json() as { version: number; musicEnabled: boolean; musicManifestUrl: string | null; threeDEnabled: boolean }
    const disabled = await page.request.put('/api/v1/admin/site', { headers: { 'X-CSRF-TOKEN': token }, data: { ...siteSettings, commentsEnabled: false } })
    expect(disabled.ok()).toBe(true)
    expect((await getCommentForm(page.request, openSlug)).commentsEnabled).toBe(false)
    const disabledSettings = await disabled.json() as { version: number; musicEnabled: boolean; musicManifestUrl: string | null; threeDEnabled: boolean }
    const restored = await page.request.put('/api/v1/admin/site', { headers: { 'X-CSRF-TOKEN': token }, data: { ...disabledSettings, commentsEnabled: true } })
    expect(restored.ok()).toBe(true)

    const rateContext = await browser.newContext({ baseURL: baseURL() })
    const rateStatuses: number[] = []
    for (let index = 0; index < 4; index += 1) {
      const form = await getCommentForm(rateContext.request, openSlug)
      await new Promise(resolve => setTimeout(resolve, 3_200))
      const response = await rateContext.request.post(`/api/v1/public/articles/${openSlug}/comments`, {
        headers: { 'X-CSRF-TOKEN': form.csrfToken },
        data: { nickname: `S4-10 限频 ${batch}`, content: `限频测试 ${index}。`, challenge: form.challenge },
      })
      rateStatuses.push(response.status())
    }
    expect(rateStatuses.slice(0, 3)).toEqual([201, 201, 201])
    expect(rateStatuses[3]).toBe(429)
    await rateContext.close()
  })

  test('covers Studio tool CRUD and title/summary/body/public search boundaries', async ({ page }) => {
    test.setTimeout(120_000)
    const batch = Date.now()
    const token = await loginAcceptanceAdmin(page.request)
    await page.goto('/studio/tools')
    await expect(page.getByRole('heading', { name: '工具控制台' })).toBeVisible()

    const category = await page.request.post('/api/v1/admin/tool-categories', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { name: `S4-10 工具分类 ${batch}`, slug: `s4-10-tools-${batch}`, description: 'S4-10 工具 CRUD 验收' },
    })
    expect(category.status()).toBe(201)
    const categoryId = (await category.json() as { id: string }).id
    const created = await page.request.post('/api/v1/admin/tools', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { categoryId, type: 'LINK', status: 'ACTIVE', title: `S4-10 工具 ${batch}`, slug: `s4-10-tool-${batch}`, description: '工具 CRUD 验收', url: 'https://example.com/s4-10', tags: ['s4-10'], sortOrder: 0 },
    })
    expect(created.status()).toBe(201)
    const tool = await created.json() as { id: string; version: number }
    const showcaseCreated = await page.request.post('/api/v1/admin/tools', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { categoryId, type: 'SHOWCASE', status: 'ACTIVE', title: `S4-10 展示 ${batch}`, slug: `s4-10-showcase-${batch}`, description: 'S4-10 展示工具验收', url: 'https://example.com/s4-10-showcase', imageUrl: 'https://example.com/s4-10-showcase.png', tags: ['s4-10', 'showcase'], sortOrder: 1 },
    })
    expect(showcaseCreated.status()).toBe(201)
    const showcaseTool = await showcaseCreated.json() as { id: string; version: number }
    const read = await page.request.get(`/api/v1/admin/tools/${tool.id}`)
    expect(read.ok()).toBe(true)
    const readTool = await read.json() as { version: number; title: string }
    expect(readTool.title).toContain('S4-10 工具')
    const updated = await page.request.put(`/api/v1/admin/tools/${tool.id}`, {
      headers: { 'X-CSRF-TOKEN': token },
      data: { version: readTool.version, categoryId, type: 'LINK', status: 'ACTIVE', title: `S4-10 工具已更新 ${batch}`, slug: `s4-10-tool-${batch}`, description: '工具 CRUD 更新', url: 'https://example.com/s4-10', tags: ['updated'], sortOrder: 1 },
    })
    expect(updated.ok()).toBe(true)
    const updatedTool = await updated.json() as { version: number }
    const directory = await page.request.get('/api/v1/public/tools')
    const publicTools = await directory.json() as { items: Array<{ type: string; componentKey: string | null }> }
    expect(publicTools.items.some(item => item.type === 'LINK')).toBe(true)
    expect(publicTools.items.some(item => item.type === 'SHOWCASE')).toBe(true)
    expect(new Set(publicTools.items.filter(item => item.type === 'EMBEDDED').map(item => item.componentKey))).toEqual(new Set(['json-format', 'base64', 'url-codec', 'timestamp', 'regex-test']))
    const removed = await page.request.delete(`/api/v1/admin/tools/${tool.id}?version=${updatedTool.version}`, { headers: { 'X-CSRF-TOKEN': token } })
    expect(removed.status()).toBe(204)
    const removedShowcase = await page.request.delete(`/api/v1/admin/tools/${showcaseTool.id}?version=${showcaseTool.version}`, { headers: { 'X-CSRF-TOKEN': token } })
    expect(removedShowcase.status()).toBe(204)

    const searchArticles = [
      ['阶段四标题命中', '无关摘要', '无关正文'],
      ['无关标题', '阶段四摘要命中', '无关正文'],
      ['无关标题正文', '无关摘要', '阶段四正文命中'],
    ] as const
    for (const [index, values] of searchArticles.entries()) {
      await createPublishedAcceptanceArticle(page.request, token, {
        slug: `s4-10-search-${Date.now()}-${index}`,
        title: `${values[0]} ${batch}`,
        excerpt: `${values[1]} ${batch}`,
        markdown: `# ${values[0]}\n\n${values[2]} ${batch}`,
      })
    }
    const hidden = await page.request.post('/api/v1/admin/articles', {
      headers: { 'X-CSRF-TOKEN': token },
      data: { slug: `s4-10-hidden-${batch}`, title: `阶段四不可公开 ${batch}`, excerpt: '不可公开摘要', markdown: '# 不可公开正文' },
    })
    expect(hidden.status()).toBe(201)
    for (const query of ['标题命中', '摘要命中', '正文命中']) {
      const response = await page.request.get('/api/v1/public/search/articles', { params: { q: `${query} ${batch}` } })
      expect(response.ok()).toBe(true)
      expect((await response.json() as { total: number }).total).toBeGreaterThan(0)
    }
    const special = await page.request.get('/api/v1/public/search/articles', { params: { q: '*[]()', page: 0, size: 1 } })
    expect(special.ok()).toBe(true)
    const paged = await page.request.get('/api/v1/public/search/articles', { params: { q: `阶段四 ${batch}`, page: 0, size: 1 } })
    expect(paged.ok()).toBe(true)
    const hiddenSearch = await page.request.get('/api/v1/public/search/articles', { params: { q: `不可公开 ${batch}` } })
    expect(hiddenSearch.ok()).toBe(true)
    expect((await hiddenSearch.json() as { total: number }).total).toBe(0)
  })
})
