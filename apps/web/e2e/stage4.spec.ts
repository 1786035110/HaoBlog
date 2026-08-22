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
