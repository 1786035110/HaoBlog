import { expect, test } from '@playwright/test'

const articlePath = '/articles/s3-08-advanced-markdown'
const contentPath = '/_content/articles/s3-08-advanced-markdown'

test.describe('stage 6 iteration 2 public read boundaries', () => {
  test('keeps rendered ETags and Save-Data variants isolated', async ({ request }) => {
    const normal = await request.get(contentPath)
    expect(normal.status()).toBe(200)
    expect(normal.headers().vary).toContain('Save-Data')
    const normalEtag = normal.headers().etag
    expect(normalEtag).toBeTruthy()

    const conditional = await request.get(contentPath, { headers: { 'If-None-Match': normalEtag! } })
    expect(conditional.status()).toBe(304)

    const saveData = await request.get(contentPath, { headers: { 'Save-Data': 'on' } })
    expect(saveData.status()).toBe(200)
    expect(saveData.headers().etag).not.toBe(normalEtag)
    expect(await saveData.text()).toContain('图像已按 Save-Data 降级')

    const missing = await request.get('/_content/articles/s6-random-missing')
    expect(missing.status()).toBe(404)
    expect(missing.headers()['content-type']).toContain('application/problem+json')
    expect(missing.headers()['cache-control']).toBe('no-store')
    expect(await missing.json()).toMatchObject({ code: 'ARTICLE_NOT_FOUND' })
  })

  test('serves twenty normal article SSR requests without capacity rejection', async ({ request }) => {
    await expect((await request.get(articlePath)).status()).toBe(200)
    const responses = await Promise.all(Array.from({ length: 20 }, () => request.get(articlePath)))
    expect(responses.map(response => response.status())).toEqual(Array(20).fill(200))
    expect((await responses[0]!.text())).toContain('S3-08 高级 Markdown 固定验收文章')
  })

  test('keeps the SSR article readable across constrained public paths', async ({ page, browser }) => {
    for (const width of [360, 768, 1280, 1600]) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(articlePath)
      await expect(page.locator('#article-title')).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
    }

    const noJs = await browser.newContext({ javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    await noJsPage.goto(articlePath)
    await expect(noJsPage.locator('#article-title')).toBeVisible()
    await expect(noJsPage.locator('.safe-markdown')).toContainText('高级 Markdown')
    await noJs.close()

    const saveData = await browser.newContext({ extraHTTPHeaders: { 'Save-Data': 'on' } })
    const saveDataPage = await saveData.newPage()
    await saveDataPage.goto(articlePath)
    await expect(saveDataPage.locator('.markdown-image-placeholder').first()).toBeVisible()
    await saveData.close()

    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto(articlePath)
    await page.keyboard.press('Tab')
    await expect(page.locator('#article-title')).toBeVisible()
  })
})
