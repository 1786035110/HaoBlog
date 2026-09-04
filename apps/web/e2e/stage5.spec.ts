import { expect, test, type APIRequestContext } from '@playwright/test'

async function navigateInApp(page: import('@playwright/test').Page, path: string) {
  await page.evaluate(async target => {
    const useNuxtApp = (window as typeof window & { useNuxtApp?: () => { $router: { push: (value: string) => Promise<unknown> } } }).useNuxtApp
    if (!useNuxtApp) throw new Error('Nuxt app is unavailable.')
    await useNuxtApp().$router.push(target)
  }, path)
  await page.waitForURL(url => url.pathname === path)
}

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
  const site = await current.json() as { version: number; commentsEnabled: boolean; musicEnabled: boolean; musicManifestUrl: string | null }
  const updated = await request.put('/api/v1/admin/site', {
    headers: { 'X-CSRF-TOKEN': token },
    data: { version: site.version, commentsEnabled: site.commentsEnabled, musicEnabled: site.musicEnabled, musicManifestUrl: site.musicManifestUrl, threeDEnabled: enabled },
  })
  expect(updated.ok()).toBe(true)
}

async function setMusicFlag(request: APIRequestContext, enabled: boolean) {
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
  const site = await current.json() as { version: number; commentsEnabled: boolean; musicEnabled: boolean; musicManifestUrl: string | null; threeDEnabled: boolean }
  const updated = await request.put('/api/v1/admin/site', {
    headers: { 'X-CSRF-TOKEN': token },
    data: { version: site.version, commentsEnabled: site.commentsEnabled, musicEnabled: enabled, musicManifestUrl: site.musicManifestUrl, threeDEnabled: site.threeDEnabled },
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

test.describe('S5-05 music and S5-06 signal repair', () => {
  test('keeps music off without requesting the manifest or audio, and preserves 404 recovery', async ({ page, browser }) => {
    const musicTestEnabled = process.env.HAOBLOG_MUSIC_TEST === 'true'
    if (musicTestEnabled) await setMusicFlag(page.request, true)
    try {
    const musicRequests: string[] = []
    page.on('request', request => {
      if (/music-manifest|\.mp3(?:\?|$)/i.test(request.url())) musicRequests.push(request.url())
    })
    const site = await page.request.get('/api/v1/public/site')
    expect(site.ok()).toBe(true)
    const sitePayload = await site.json() as { musicEnabled: boolean; musicManifestUrl: string | null }
    await page.goto('/')
    if (!sitePayload.musicEnabled) {
      await expect(page.getByRole('button', { name: 'MUSIC / OPEN CONSOLE' })).toHaveCount(0)
      expect(musicRequests).toEqual([])
    } else {
      expect(sitePayload.musicManifestUrl).toBeTruthy()
      await expect(page.getByRole('button', { name: 'MUSIC / OPEN CONSOLE' })).toBeVisible()
      await expect(page.locator('audio')).toHaveCount(0)
      await page.getByRole('button', { name: 'MUSIC / OPEN CONSOLE' }).click()
      await expect(page.locator('audio')).toBeAttached()
      await expect(page.locator('audio')).toHaveAttribute('crossorigin', 'anonymous')
      await expect.poll(() => musicRequests.filter(url => url === sitePayload.musicManifestUrl).length).toBeGreaterThan(0)
      expect(musicRequests.filter(url => /\.mp3(?:\?|$)/i.test(url))).toEqual([])
      await expect(page.locator('audio')).toHaveJSProperty('paused', true)
    }

    const notFound = await page.goto('/s5-06-missing-signal')
    expect(notFound?.status()).toBe(404)
    await expect(page.getByRole('heading', { name: /页面未找到/ })).toBeVisible()
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex,nofollow')
    await expect(page.getByRole('searchbox', { name: '搜索文章' })).toBeVisible()
    await expect(page.getByRole('link', { name: '文章观测日志' })).toHaveAttribute('href', '/articles')
    await expect(page.getByRole('link', { name: '公开工具箱' })).toHaveAttribute('href', '/tools')
    await expect(page.getByRole('link', { name: '返回首页' })).toHaveAttribute('href', '/')

    const mobile = await browser.newContext({
      baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
      hasTouch: true,
      isMobile: true,
      extraHTTPHeaders: { 'Save-Data': 'on' },
    })
    const mobilePage = await mobile.newPage()
    await mobilePage.setViewportSize({ width: 360, height: 800 })
    await mobilePage.goto('/s5-06-mobile-signal')
    await expect(mobilePage.getByRole('searchbox', { name: '搜索文章' })).toBeVisible()
    const mobileLauncher = mobilePage.getByRole('button', { name: '玩个小游戏' })
    if (await mobileLauncher.count()) {
      await mobileLauncher.click()
      await expect(mobilePage.getByRole('heading', { name: '选一个小游戏' })).toBeVisible()
    }
    await mobile.close()

    const noJs = await browser.newContext({
      baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
      javaScriptEnabled: false,
    })
    const noJsPage = await noJs.newPage()
    await noJsPage.goto('/s5-06-no-js')
    await expect(noJsPage.getByRole('searchbox', { name: '搜索文章' })).toBeVisible()
    await expect(noJsPage.getByRole('link', { name: '返回首页' })).toBeVisible()
    await noJs.close()
    } finally {
      if (musicTestEnabled) await setMusicFlag(page.request, false)
    }
  })

  test('offers keyboard-accessible games on a normal desktop 404 when enabled', async ({ page }) => {
    await page.goto('/s5-06-keyboard-signal')
    const launcher = page.getByRole('button', { name: '玩个小游戏' })
    if (await launcher.count() === 0) return
    await launcher.click()
    await page.getByRole('button', { name: /贪吃蛇/ }).click()
    await expect(page.locator('.mini-game').filter({ hasText: '贪吃蛇' })).toBeVisible()
    await page.getByRole('button', { name: '开始', exact: true }).click()
    await page.keyboard.press('ArrowRight')
  })
})

test.describe('S5-08 performance and lifecycle closure', () => {
  test('keeps 360/768/1280/1600 layouts readable and handles Canvas, WebGL and Service Worker failures', async ({ page, browser }) => {
    for (const width of [360, 768, 1280, 1600]) {
      await page.setViewportSize({ width, height: 900 })
      for (const path of ['/', '/garden']) {
        await page.goto(path)
        await expect(page.locator('h1')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width)
      }
    }

    const canvasFailure = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' })
    await canvasFailure.addInitScript(() => {
      const original = HTMLCanvasElement.prototype.getContext
      HTMLCanvasElement.prototype.getContext = function (type: string, ...args: unknown[]) {
        if (type === '2d') return null
        return original.call(this, type, ...args as [])
      } as typeof HTMLCanvasElement.prototype.getContext
    })
    const canvasPage = await canvasFailure.newPage()
    await canvasPage.setViewportSize({ width: 1280, height: 900 })
    await canvasPage.goto('/garden')
    await expect(canvasPage.locator('.garden-canvas-panel')).toHaveCount(0)
    await expect(canvasPage.getByRole('heading', { name: '可读的知识时间线' })).toBeVisible()
    await canvasFailure.close()

    await setThreeDFlag(page.request, true)
    try {
      const webglFailure = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' })
      await webglFailure.addInitScript(() => {
        const original = HTMLCanvasElement.prototype.getContext
        HTMLCanvasElement.prototype.getContext = function (type: string, ...args: unknown[]) {
          if (/^webgl/.test(type)) return null
          return original.call(this, type, ...args as [])
        } as typeof HTMLCanvasElement.prototype.getContext
      })
      const webglPage = await webglFailure.newPage()
      await webglPage.setViewportSize({ width: 1280, height: 900 })
      const heavyRequests: string[] = []
      webglPage.on('request', request => {
        if (/HomeThreeScene|C5rh5wLt2|\/public\/garden/i.test(request.url())) heavyRequests.push(request.url())
      })
      await webglPage.goto('/')
      await webglPage.locator('#starmap-scene').scrollIntoViewIfNeeded()
      await expect(webglPage.locator('.home-starmap-status')).toContainText('动态星图不可用')
      expect(heavyRequests).toEqual([])
      await webglFailure.close()
    } finally {
      await setThreeDFlag(page.request, false)
    }

    const swFailure = await browser.newContext({
      baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
      serviceWorkers: 'block',
    })
    const swPage = await swFailure.newPage()
    await swPage.goto('/tools')
    await swPage.getByRole('button', { name: '准备离线工具' }).click()
    await swPage.getByRole('button', { name: '确认准备' }).click()
    await expect(swPage.getByText(/Service Worker 暂时不可用|离线工具准备失败/)).toBeVisible({ timeout: 20_000 })
    await expect(swPage.getByRole('heading', { name: '工具目录' })).toBeVisible()
    await swFailure.close()
  })

  test('does not accumulate Canvas, RAF or global listeners across home, garden and Studio routes', async ({ page }) => {
    await page.addInitScript(() => {
      const originalRequest = window.requestAnimationFrame.bind(window)
      const originalCancel = window.cancelAnimationFrame.bind(window)
      const pending = new Set<number>()
      window.requestAnimationFrame = callback => {
        let id = 0
        id = originalRequest(time => {
          pending.delete(id)
          callback(time)
        })
        pending.add(id)
        return id
      }
      window.cancelAnimationFrame = id => {
        pending.delete(id)
        originalCancel(id)
      }

      const records: Array<{ target: EventTarget; type: string; listener: EventListenerOrEventListenerObject; capture: boolean }> = []
      const originalAdd = EventTarget.prototype.addEventListener
      const originalRemove = EventTarget.prototype.removeEventListener
      const capture = (options?: boolean | AddEventListenerOptions) => typeof options === 'boolean' ? options : Boolean(options?.capture)
      EventTarget.prototype.addEventListener = function (type, listener, options) {
        if ((this === window || this === document) && listener && !records.some(record => record.target === this && record.type === type && record.listener === listener && record.capture === capture(options))) {
          records.push({ target: this, type, listener, capture: capture(options) })
        }
        return originalAdd.call(this, type, listener, options)
      }
      EventTarget.prototype.removeEventListener = function (type, listener, options) {
        const index = records.findIndex(record => record.target === this && record.type === type && record.listener === listener && record.capture === capture(options))
        if (index >= 0) records.splice(index, 1)
        return originalRemove.call(this, type, listener, options)
      }
      Object.defineProperty(window, '__s5Lifecycle', {
        value: { snapshot: () => ({ raf: pending.size, listeners: records.length }) },
      })
    })

    await setThreeDFlag(page.request, true)
    try {
      await page.setViewportSize({ width: 1280, height: 900 })
      await page.goto('/articles')
      const snapshot = () => page.evaluate(() => (window as typeof window & { __s5Lifecycle: { snapshot: () => { raf: number; listeners: number } } }).__s5Lifecycle.snapshot())
      await expect.poll(async () => (await snapshot()).raf).toBe(0)
      await navigateInApp(page, '/')
      await page.locator('#starmap-scene').scrollIntoViewIfNeeded()
      await page.waitForTimeout(300)
      await navigateInApp(page, '/garden')
      await page.waitForTimeout(300)
      await navigateInApp(page, '/studio')
      await navigateInApp(page, '/articles')
      await expect(page.locator('canvas')).toHaveCount(0)
      await expect.poll(async () => (await snapshot()).raf).toBe(0)
      const baseline = await snapshot()

      for (let index = 0; index < 3; index += 1) {
        await navigateInApp(page, '/')
        await page.locator('#starmap-scene').scrollIntoViewIfNeeded()
        await page.waitForTimeout(300)
        await navigateInApp(page, '/garden')
        await page.waitForTimeout(300)
        await navigateInApp(page, '/studio')
        await navigateInApp(page, '/articles')
        await expect(page.locator('canvas')).toHaveCount(0)
        await expect.poll(async () => (await snapshot()).raf).toBe(0)
        expect((await snapshot()).listeners).toBeLessThanOrEqual(baseline.listeners)
      }
    } finally {
      await setThreeDFlag(page.request, false)
    }
  })

  test('closes AudioContext in Studio and keeps playback usable when AudioContext fails', async ({ page, browser }) => {
    test.skip(process.env.HAOBLOG_MUSIC_TEST !== 'true', '需要阶段五专用音乐清单配置。')
    await setMusicFlag(page.request, true)
    const manifest = {
      version: 1,
      tracks: [{
        id: 'acceptance-signal',
        title: 'Acceptance Signal',
        artist: 'HaoBlog Test',
        audioUrl: 'https://media.example.test/acceptance.mp3',
        licenseName: 'Test fixture',
      }],
    }
    try {
      const site = await page.request.get('/api/v1/public/site')
      const { musicManifestUrl } = await site.json() as { musicManifestUrl: string }
      await page.route(musicManifestUrl, route => route.fulfill({ json: manifest }))
      await page.route('https://media.example.test/**', route => route.fulfill({ contentType: 'audio/mpeg', body: '' }))
      await page.addInitScript(() => {
        const state = { created: 0, closed: 0 }
        class FakeAnalyser {
          fftSize = 64
          smoothingTimeConstant = 0
          frequencyBinCount = 32
          connect() {}
          disconnect() {}
          getByteFrequencyData(values: Uint8Array) { values.fill(0) }
        }
        class FakeAudioContext {
          state = 'running'
          destination = {}
          constructor() { state.created += 1 }
          createMediaElementSource() { return { connect() {}, disconnect() {} } }
          createAnalyser() { return new FakeAnalyser() }
          resume() { return Promise.resolve() }
          close() { state.closed += 1; return Promise.resolve() }
        }
        Object.defineProperty(window, 'AnalyserNode', { configurable: true, value: FakeAnalyser })
        Object.defineProperty(window, 'AudioContext', { configurable: true, value: FakeAudioContext })
        Object.defineProperty(window, '__s5Audio', { value: state })
        HTMLMediaElement.prototype.play = function () { this.dispatchEvent(new Event('play')); return Promise.resolve() }
        HTMLMediaElement.prototype.pause = function () { this.dispatchEvent(new Event('pause')) }
      })
      await page.goto('/')
      await page.getByRole('button', { name: 'MUSIC / OPEN CONSOLE' }).click()
      await page.getByRole('button', { name: '开始播放' }).click()
      await expect.poll(() => page.evaluate(() => (window as typeof window & { __s5Audio: { created: number } }).__s5Audio.created)).toBe(1)
      await navigateInApp(page, '/studio')
      await expect.poll(() => page.evaluate(() => (window as typeof window & { __s5Audio: { closed: number } }).__s5Audio.closed)).toBe(1)

      const audioFailure = await browser.newContext({ baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1' })
      await audioFailure.addInitScript(() => {
        class FakeAnalyser {}
        Object.defineProperty(window, 'AnalyserNode', { configurable: true, value: FakeAnalyser })
        Object.defineProperty(window, 'AudioContext', { configurable: true, value: class { constructor() { throw new Error('blocked') } } })
        HTMLMediaElement.prototype.play = function () { this.dispatchEvent(new Event('play')); return Promise.resolve() }
        HTMLMediaElement.prototype.pause = function () { this.dispatchEvent(new Event('pause')) }
      })
      const audioFailurePage = await audioFailure.newPage()
      await audioFailurePage.route(musicManifestUrl, route => route.fulfill({ json: manifest }))
      await audioFailurePage.route('https://media.example.test/**', route => route.fulfill({ contentType: 'audio/mpeg', body: '' }))
      await audioFailurePage.goto('/')
      await audioFailurePage.getByRole('button', { name: 'MUSIC / OPEN CONSOLE' }).click()
      await audioFailurePage.getByRole('button', { name: '开始播放' }).click()
      await expect(audioFailurePage.getByText('频谱接入失败，已保留普通播放。')).toBeVisible()
      await expect(audioFailurePage.getByRole('button', { name: '暂停播放' })).toBeVisible()
      await audioFailure.close()
    } finally {
      await setMusicFlag(page.request, false)
    }
  })
})

test.describe('S5-07 PWA offline tools', () => {
  test('registers only in the production/test path and prepares an explicit cache boundary', async ({ page }) => {
    const manifest = await page.request.get('/manifest.webmanifest')
    test.skip(!manifest.ok(), 'PWA 只在 production build 或明确测试模式启用。')
    expect(manifest.headers()['content-type']).toMatch(/manifest|json/)
    const manifestJson = await manifest.json() as { theme_color: string; icons: Array<{ sizes: string; purpose?: string }> }
    expect(manifestJson.theme_color).toBe('#06110F')
    expect(manifestJson.icons.map(icon => icon.sizes)).toEqual(expect.arrayContaining(['192x192', '512x512']))
    expect(manifestJson.icons.some(icon => icon.purpose === 'maskable')).toBe(true)

    await page.goto('/tools')
    await expect(page.getByRole('button', { name: '准备离线工具' })).toBeVisible()
    await page.getByRole('button', { name: '准备离线工具' }).click()
    await page.getByRole('button', { name: '确认准备' }).click()
    await expect(page.getByText(/五个内嵌工具及当前工具目录已准备/)).toBeVisible({ timeout: 20_000 })

    const cachedUrls = await page.evaluate(async () => {
      const urls: string[] = []
      for (const key of await caches.keys()) {
        for (const request of await (await caches.open(key)).keys()) urls.push(request.url)
      }
      return urls
    })
    expect(cachedUrls.some(url => /\/tools(?:\?|$)/.test(url))).toBe(true)
    expect(cachedUrls.some(url => /\/api\/|\/studio\/|\/articles\/|\/comments|\/garden|music-manifest|\.mp3(?:\?|$)/i.test(url))).toBe(false)

    await page.context().setOffline(true)
    await page.reload()
    await expect(page.getByRole('heading', { name: '工具目录' })).toBeVisible()
    await page.context().setOffline(false)
  })
})
