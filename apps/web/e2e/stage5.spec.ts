import { expect, test, type APIRequestContext } from '@playwright/test'

test.describe('S5-01/S5-02 knowledge garden', () => {
  test('keeps the garden readable in SSR, no-JS, 360px, Save-Data and reduced-motion paths', async ({ page, browser }) => {
    const ssr = await page.request.get('/garden')
    expect(ssr.status()).toBe(200)
    const html = await ssr.text()
    expect(html).toContain('知识星图')
    expect(html).toContain('可读的知识时间线')
    expect(html).toContain('rel="canonical"')
    expect(html).not.toContain('noindex')

    const graph = await page.request.get('/api/v1/public/garden')
    expect(graph.ok()).toBe(true)
    const payload = await graph.json() as { nodes: unknown[]; edges: unknown[]; truncated: boolean }
    expect(payload.nodes.length).toBeLessThanOrEqual(200)
    expect(payload.edges.length).toBeLessThanOrEqual(600)
    expect(typeof payload.truncated).toBe('boolean')

    const noJs = await browser.newContext({
      baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
      javaScriptEnabled: false,
    })
    const noJsPage = await noJs.newPage()
    await noJsPage.setViewportSize({ width: 360, height: 900 })
    const noJsResponse = await noJsPage.goto('/garden')
    expect(noJsResponse?.status()).toBe(200)
    await expect(noJsPage.getByRole('heading', { name: '知识星图' })).toBeVisible()
    await expect(noJsPage.getByRole('heading', { name: '可读的知识时间线' })).toBeVisible()
    expect(await noJsPage.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
    await noJs.close()

    const constrainedContexts = [
      {
        context: await browser.newContext({
          baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
          extraHTTPHeaders: { 'Save-Data': 'on' },
        }),
        reducedMotion: false,
      },
      {
        context: await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' }),
        reducedMotion: true,
      },
    ]
    for (const { context, reducedMotion } of constrainedContexts) {
      const constrainedPage = await context.newPage()
      if (reducedMotion) await constrainedPage.emulateMedia({ reducedMotion: 'reduce' })
      const dynamicRequests: string[] = []
      constrainedPage.on('request', request => {
        if (/d3-force/i.test(request.url())) dynamicRequests.push(request.url())
      })
      await constrainedPage.goto('/garden')
      await expect(constrainedPage.getByRole('heading', { name: '知识星图' })).toBeVisible()
      await expect(constrainedPage.locator('.garden-canvas-panel')).toHaveCount(0)
      expect(dynamicRequests).toEqual([])
      await context.close()
    }
  })

  test('loads desktop force graph with keyboard-accessible preview and locates a tool query', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.goto('/garden')
    const graph = await page.request.get('/api/v1/public/garden')
    expect(graph.ok()).toBe(true)
    const payload = await graph.json() as { nodes: Array<{ href: string }> }

    if (payload.nodes.length) {
      await expect(page.locator('.garden-canvas-panel')).toBeVisible()
      const firstNode = page.locator('.garden-node-button').first()
      await firstNode.focus()
      await page.keyboard.press('Enter')
      await expect(page.locator('.garden-preview')).toBeVisible()
      await expect(page.getByRole('link', { name: /打开内容/ })).toHaveAttribute('href', payload.nodes[0].href)
    }

    const tools = await page.request.get('/api/v1/public/tools')
    expect(tools.ok()).toBe(true)
    const tool = (await tools.json() as { items: Array<{ slug: string }> }).items[0]
    if (!tool) return
    await page.goto(`/tools?tool=${encodeURIComponent(tool.slug)}`)
    const entry = page.locator(`#tool-${tool.slug}`)
    await expect(entry).toBeVisible()
    await expect(entry.getByRole('button', { name: 'CLOSE' })).toHaveAttribute('aria-expanded', 'true')
  })
})

async function setThreeDFlag(request: APIRequestContext, enabled: boolean) {
  const anonymousCsrf = await request.get('/api/v1/admin/csrf')
  expect(anonymousCsrf.ok()).toBe(true)
  const anonymousToken = (await anonymousCsrf.json() as { token: string }).token
  const login = await request.post('/api/v1/admin/session', {
    headers: { 'X-CSRF-TOKEN': anonymousToken },
    data: { username: 'admin', password: 'password' },
  })
  expect(login.ok()).toBe(true)
  const csrf = await request.get('/api/v1/admin/csrf')
  const token = (await csrf.json() as { token: string }).token
  const current = await request.get('/api/v1/admin/site')
  expect(current.ok()).toBe(true)
  const site = await current.json() as { version: number; commentsEnabled: boolean; musicEnabled: boolean }
  const updated = await request.put('/api/v1/admin/site', {
    headers: { 'X-CSRF-TOKEN': token },
    data: { version: site.version, commentsEnabled: site.commentsEnabled, musicEnabled: site.musicEnabled, threeDEnabled: enabled },
  })
  expect(updated.ok()).toBe(true)
}

test.describe('S5-03 secure terminal and theme', () => {
  test('opens the native dialog, supports keyboard recovery and safe command fallback', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: '⌘K' }).click()
    const dialog = page.getByRole('dialog', { name: '安全信号终端' })
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('input')).toBeFocused()
    await expect(dialog).toHaveAttribute('aria-modal', 'true')

    await page.keyboard.type('help')
    await page.keyboard.press('Enter')
    await expect(dialog).toContainText('ls posts')
    await page.keyboard.press('ArrowUp')
    await expect(dialog.locator('input')).toHaveValue('help')
    await page.keyboard.press('ArrowDown')
    await expect(dialog.locator('input')).toHaveValue('')
    await page.keyboard.type('ask "如何实现 RAG"')
    await page.keyboard.press('Enter')
    await expect(dialog).toContainText('AI 信号尚未开放')
    await expect(dialog.getByRole('link', { name: '打开 →' }).last()).toHaveAttribute('href', /\/search\?q=/)
    await page.keyboard.type('rm -rf /')
    await page.keyboard.press('Enter')
    await expect(dialog).toContainText('命令未执行')

    await page.keyboard.press('Control+L')
    await expect(dialog.locator('input')).toHaveValue('')
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('button', { name: '⌘K' })).toBeFocused()
  })

  test('restores, persists and toggles only the two supported themes', async ({ page }) => {
    await page.goto('/')
    await page.evaluate(() => localStorage.clear())
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'night')
    await page.locator('.home-title-button').click({ clickCount: 5 })
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'blueprint')
    expect(await page.evaluate(() => localStorage.getItem('haoblog-theme'))).toBe('blueprint')
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'blueprint')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.locator('.home-title-button').click({ clickCount: 5 })
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'night')
    expect(await page.locator('html').getAttribute('data-theme-transition')).toBeNull()
  })
})

test.describe('S5-04 home three frames', () => {
  test('keeps all three frames in SSR and does not load Three or graph before the flag/viewport gate', async ({ page, browser }) => {
    await setThreeDFlag(page.request, false)
    const ssr = await page.request.get('/')
    const html = await ssr.text()
    expect(ssr.status()).toBe(200)
    expect(html).toContain('夜空校准')
    expect(html).toContain('星图接入')
    expect(html).toContain('近期观测日志')
    expect(html).toContain('继续校准')

    const noJs = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    await noJsPage.goto('/')
    await expect(noJsPage.getByRole('heading', { name: '近期观测日志' })).toBeVisible()
    await expect(noJsPage.getByRole('link', { name: /继续校准/ })).toBeVisible()
    await noJs.close()

    const requests: string[] = []
    page.on('request', request => requests.push(request.url()))
    await page.goto('/')
    await page.waitForTimeout(300)
    expect(requests.filter(url => /three|\/public\/garden/i.test(url))).toEqual([])
    await expect(page.locator('.home-starmap-fallback')).toBeVisible()
  })

  test('gates graph and Three by Save-Data, reduced-motion and mobile paths, then loads on desktop viewport entry when enabled', async ({ page, browser }) => {
    await setThreeDFlag(page.request, true)
    try {
      const requests: string[] = []
      page.on('request', request => requests.push(request.url()))
      await page.setViewportSize({ width: 1280, height: 800 })
      await page.goto('/')
      await page.waitForTimeout(250)
      expect(requests.filter(url => /three|\/public\/garden/i.test(url))).toEqual([])
      await page.locator('#starmap-scene').scrollIntoViewIfNeeded()
      await expect.poll(() => requests.filter(url => /\/public\/garden/i.test(url)).length, { timeout: 10_000 }).toBeGreaterThan(0)
      await expect(page.locator('.home-starmap-status')).toContainText(/接入|回退|暂无/)

      const constrained = [
        await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', extraHTTPHeaders: { 'Save-Data': 'on' } }),
        await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' }),
        await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', hasTouch: true, isMobile: true }),
      ]
      for (const context of constrained) {
        const constrainedPage = await context.newPage()
        if (context === constrained[1]) await constrainedPage.emulateMedia({ reducedMotion: 'reduce' })
        const constrainedRequests: string[] = []
        constrainedPage.on('request', request => constrainedRequests.push(request.url()))
        await constrainedPage.goto('/')
        await constrainedPage.locator('#starmap-scene').scrollIntoViewIfNeeded()
        await constrainedPage.waitForTimeout(300)
        expect(constrainedRequests.filter(url => /three|\/public\/garden/i.test(url))).toEqual([])
        await expect(constrainedPage.locator('.home-static-starmap')).toBeVisible()
        await context.close()
      }
    } finally {
      await setThreeDFlag(page.request, false)
    }
  })
})
