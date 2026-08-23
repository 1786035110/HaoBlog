import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = resolve(import.meta.dirname, '../app')
const publicDir = resolve(import.meta.dirname, '../public')

describe('phase five static boundaries', () => {
  it('keeps music and PWA bodies out of the article route and music behind interaction', () => {
    const layout = readFileSync(resolve(app, 'layouts/default.vue'), 'utf8')
    const article = readFileSync(resolve(app, 'pages/articles/[slug].vue'), 'utf8')
    expect(layout).toContain("import('../components/music/SignalTape.client.vue')")
    expect(article).not.toContain('SignalTape')
    expect(article).not.toContain('preloadEmbeddedToolChunks')
    expect(readFileSync(resolve(app, 'components/music/SignalTape.client.vue'), 'utf8')).toContain('preload="none"')
  })

  it('keeps PWA cache boundaries explicit and excludes API, Studio, articles, graph, comments and audio', () => {
    const worker = readFileSync(resolve(publicDir, 'sw.js'), 'utf8')
    expect(worker).toContain("url.pathname === '/tools'")
    expect(worker).toContain("url.pathname.startsWith('/_nuxt/')")
    expect(worker).toContain("url.pathname.startsWith('/workers/')")
    expect(worker).not.toContain("url.pathname.startsWith('/api/')")
    expect(worker).not.toContain("url.pathname.startsWith('/studio/')")
    expect(worker).not.toContain("url.pathname.startsWith('/articles/')")
    expect(worker).not.toContain('music-manifest')
    expect(worker).not.toContain('.mp3')
    expect(worker).toContain('PREPARE_TOOLS')
    expect(worker).toContain('networkFirstToolPage')
  })

  it('keeps 404 enhancement dynamic and preserves generic error recovery', () => {
    const error = readFileSync(resolve(app, 'error.vue'), 'utf8')
    expect(error).toContain("import('./components/error/SignalRepair.client.vue')")
    expect(error).toContain("window.innerWidth <= 360")
    expect(error).toContain("pointer: coarse")
    expect(error).toContain('prefers-reduced-motion')
    expect(error).toContain('retry-button')
    expect(error).toContain('!isNotFound')
    expect(error).toContain('当前请求没有得到可用的观测响应，请稍后重试。')
    expect(error).toContain('noindex,nofollow')
  })

  it('declares normal and maskable 192/512 install icons', () => {
    const manifest = readFileSync(resolve(publicDir, 'manifest.webmanifest'), 'utf8')
    expect(manifest).toContain('192x192')
    expect(manifest).toContain('512x512')
    expect(manifest).toContain('purpose": "maskable"')
    for (const file of ['pwa-192.svg', 'pwa-512.svg', 'pwa-192-maskable.svg', 'pwa-512-maskable.svg']) {
      expect(readFileSync(resolve(publicDir, file), 'utf8')).toContain('<svg')
    }
  })
})
