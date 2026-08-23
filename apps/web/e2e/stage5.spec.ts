import { expect, test } from '@playwright/test'

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
