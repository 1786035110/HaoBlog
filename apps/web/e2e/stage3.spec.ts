import { expect, test } from '@playwright/test'

const articlePath = '/articles/s3-07-advanced-markdown'
const publicPaths = ['/', '/articles', articlePath, '/garden', '/tools', '/about']
const viewports = [360, 768, 1280, 1600]

test.describe('S3-07 public reading acceptance', () => {
  test('keeps public pages inside the viewport at all required widths', async ({ page }) => {
    for (const width of viewports) {
      await page.setViewportSize({ width, height: 900 })
      for (const path of publicPaths) {
        await page.goto(path)
        await expect(page.locator('main')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
      }
    }
  })

  test('supports no JavaScript, Save-Data and reduced-motion', async ({ browser }) => {
    const noJs = await browser.newContext({ javaScriptEnabled: false })
    const noJsPage = await noJs.newPage()
    const noJsResponse = await noJsPage.goto(articlePath)
    expect(noJsResponse?.status()).toBe(200)
    await expect(noJsPage.locator('#article-title')).toBeVisible()
    await expect(noJsPage.getByText('正文、公式和代码在 SSR HTML 中直接可读。')).toBeVisible()
    await noJs.close()

    const saveData = await browser.newContext({ extraHTTPHeaders: { 'Save-Data': 'on' } })
    const saveDataPage = await saveData.newPage()
    const imageRequests: string[] = []
    saveDataPage.on('request', request => { if (request.resourceType() === 'image') imageRequests.push(request.url()) })
    await saveDataPage.goto(articlePath)
    await expect(saveDataPage.locator('html')).toHaveAttribute('data-save-data', 'on')
    await expect(saveDataPage.locator('.markdown-image-placeholder')).toContainText('观测站静态封面')
    expect(imageRequests).toEqual([])
    await saveData.close()

    const reduced = await browser.newContext()
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

  test('exposes visible keyboard focus for public controls', async ({ page }) => {
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
          tag: element.tagName,
          className: element.className,
          outline: `${style.outlineStyle} ${style.outlineWidth}`,
        }
      })
      expect(focusState.visible, `${focusState.tag}.${focusState.className} outline=${focusState.outline}`).toBe(true)
    }
  })
})
