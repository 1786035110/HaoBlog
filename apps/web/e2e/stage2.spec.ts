import { expect, test, type Browser, type Page } from '@playwright/test'

const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

async function login(page: Page) {
  await page.goto('/studio')
  await page.getByLabel('管理员标识').fill(process.env.HAOBLOG_E2E_ADMIN_USERNAME || 'admin')
  await page.getByLabel('访问密钥').fill(process.env.HAOBLOG_E2E_ADMIN_PASSWORD || 'password')
  await page.getByRole('button', { name: '建立安全会话' }).click()
  await expect(page).toHaveURL(/\/studio\/articles$/)
}

async function createArticle(page: Page, slug: string, title: string) {
  await expect(page).toHaveURL(/\/studio\/articles$/)
  await page.goto('/studio/articles')
  await expect(page.locator('#articles-title')).toBeVisible()
  await page.getByLabel('工作台导航').getByRole('link', { name: '新建文章' }).click()
  await expect(page).toHaveURL(/\/studio\/articles\/(?!new$)[^/]+$/)
  await page.locator('#article-title').fill(title)
  await page.locator('#article-slug').fill(slug)
  await page.locator('#article-excerpt').fill('阶段二验收摘要')
  await page.locator('#article-seo-description').fill('阶段二验收 Meta 描述')
  await page.locator('#article-markdown').fill(`# ${title}\n\n阶段二验收正文 ${slug}`)
  await page.getByRole('button', { name: '手动保存' }).click()
  await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
}

function localDateTimeAfter(seconds: number) {
  const date = new Date(Date.now() + seconds * 1000)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find(part => part.type === type)?.value || ''
  return `${value('year')}-${value('month')}-${value('day')}T${value('hour')}:${value('minute')}:${value('second')}`
}

test.describe.serial('S2-10 content acceptance', () => {
  test('login, draft, autosave, recovery, media, preview, publish and no-JS SSR', async ({ page, browser }) => {
    const slug = `e2e-${Date.now()}`
    const title = `阶段二验收 ${slug}`
    await login(page)
    await createArticle(page, slug, title)

    await page.locator('#article-markdown').fill(`# ${title}\n\n自动保存正文`)
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced', { timeout: 20_000 })

    await page.route('**/api/v1/admin/media/uploads', route => route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ uploadId: '00000000-0000-0000-0000-000000000001', objectKey: 'media/e2e.png', uploadUrl: 'https://upload.test/put', fields: {}, expiresAt: new Date(Date.now() + 300_000).toISOString() }),
    }))
    await page.route('https://upload.test/**', route => route.fulfill({ status: 204 }))
    await page.route('**/api/v1/admin/media/uploads/**', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: '00000000-0000-0000-0000-000000000002', objectKey: 'media/e2e.png', publicUrl: 'https://cdn.test/media/e2e.png', mimeType: 'image/png', sizeBytes: 68, width: 1, height: 1, status: 'AVAILABLE' }),
    }))
    await page.locator('input[type=file]').setInputFiles({ name: 'pixel.png', mimeType: 'image/png', buffer: pixel })
    await expect(page.getByText(/已确认/)).toBeVisible({ timeout: 10_000 })

    await page.route('**/api/v1/admin/articles/*', route => route.request().method() === 'PUT' ? route.abort() : route.continue())
    await page.locator('#article-title').fill(`${title} 本地副本`)
    await page.getByRole('button', { name: '手动保存' }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'error')
    await page.unroute('**/api/v1/admin/articles/*')
    await page.reload()
    await expect(page.getByRole('heading', { name: '发现本地灾难副本' })).toBeVisible()
    await page.getByRole('button', { name: '恢复本地副本' }).click()
    await expect(page.locator('#article-title')).toHaveValue(`${title} 本地副本`)
    await page.getByRole('button', { name: '手动保存' }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')

    await page.getByRole('button', { name: '生成预览' }).click()
    const previewLink = page.getByRole('link', { name: '打开限时预览' })
    await expect(previewLink).toBeVisible()
    const previewPage = await page.context().newPage()
    await previewPage.goto(await previewLink.getAttribute('href') as string)
    await expect(previewPage.getByRole('heading', { name: `${title} 本地副本` })).toBeVisible()
    await previewPage.close()

    await page.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
    await expect(page.getByText('当前工作副本已保存。')).toBeVisible()

    const noJsContext = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1', javaScriptEnabled: false })
    const noJsPage = await noJsContext.newPage()
    const response = await noJsPage.goto(`/articles/${slug}`)
    expect(response?.status()).toBe(200)
    const html = await noJsPage.content()
    expect(html).toContain(`${title} 本地副本`)
    expect(html).toContain('本地副本')
    expect(html).toContain('meta name="description"')
    expect(html).toContain('property="og:title"')
    await noJsContext.close()
  })

  test('two pages return 409 without losing the losing copy', async ({ browser, page }) => {
    const slug = `conflict-${Date.now()}`
    await login(page)
    await createArticle(page, slug, `并发保存 ${slug}`)
    const articleUrl = page.url()
    const second = await page.context().newPage()
    await second.goto(articleUrl)
    await expect(second.locator('#article-markdown')).toBeVisible()
    await page.locator('#article-markdown').fill('# 页面 A\n\n胜者内容')
    await page.getByRole('button', { name: '手动保存' }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
    await second.locator('#article-markdown').fill('# 页面 B\n\n冲突副本')
    await second.getByRole('button', { name: '手动保存' }).click()
    await expect(second.locator('[data-status]')).toHaveAttribute('data-status', 'conflict')
    await expect(second.getByText(/本地副本已保留/)).toBeVisible()
    const draft = await second.evaluate(() => new Promise<{ form?: { markdown?: string } } | undefined>((resolve, reject) => {
      const open = indexedDB.open('haoblog-studio', 1)
      open.onerror = () => reject(open.error)
      open.onsuccess = () => {
        const request = open.result.transaction('article-drafts', 'readonly').objectStore('article-drafts').get(location.pathname.split('/').pop())
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
      }
    }))
    expect(draft?.form?.markdown).toContain('冲突副本')
    await second.close()
  })

  test('version restore and scheduled publication complete through the Studio', async ({ page }) => {
    const slug = `schedule-${Date.now()}`
    await login(page)
    await createArticle(page, slug, `调度验收 ${slug}`)
    await page.locator('#article-markdown').fill('# V1\n\n第一版正文')
    await page.getByRole('button', { name: '手动保存' }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
    await page.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
    await expect(page.getByRole('button', { name: '发布', exact: true })).toBeEnabled()
    await page.locator('#article-markdown').fill('# V2\n\n第二版正文')
    await page.getByRole('button', { name: '手动保存' }).click()
    await page.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.getByRole('button', { name: '发布', exact: true })).toBeEnabled()
    await page.getByRole('link', { name: '历史版本 / 比较与恢复' }).click()
    await expect(page).toHaveURL(/\/versions$/)
    await page.getByRole('button', { name: '恢复到工作副本' }).last().click()
    await page.getByRole('button', { name: /确认恢复/ }).click()
    await page.getByRole('link', { name: '返回编辑' }).click()
    await expect(page.locator('#article-markdown')).toHaveValue(/第一版正文/)

    await page.locator('#article-title').fill(`定时 ${slug}`)
    await page.locator('#article-slug').fill(`${slug}-scheduled`)
    await page.locator('#article-scheduled-at').fill(localDateTimeAfter(6))
    await page.getByRole('button', { name: '定时发布' }).click()
    await expect(page.locator('[data-status]')).toHaveAttribute('data-status', 'synced')
    await expect.poll(async () => (await page.goto(`/api/v1/public/articles/${slug}-scheduled`))?.status(), { timeout: 20_000 }).toBe(200)
  })
})
