import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = resolve(import.meta.dirname, '../app')
const publicDir = resolve(import.meta.dirname, '../public')

describe('phase five static boundaries', () => {
  it('keeps music and PWA bodies out of the article route and music behind interaction', () => {
    const layout = readFileSync(resolve(app, 'layouts/default.vue'), 'utf8')
    const article = readFileSync(resolve(app, 'pages/articles/[slug].vue'), 'utf8')
    expect(layout).toContain("import('../components/music/MusicConsole.client.vue')")
    expect(article).not.toContain('MusicConsole')
    expect(article).not.toContain('preloadEmbeddedToolChunks')
    expect(readFileSync(resolve(app, 'components/music/MusicConsole.client.vue'), 'utf8')).toContain('preload="none"')
  })

  it('releases every stage five heavyweight owner on unmount', () => {
    const home = readFileSync(resolve(app, 'components/home/HomeThreeScene.client.vue'), 'utf8')
    expect(home).toContain('cancelAnimationFrame(raf)')
    expect(home).toContain('nodeGeometry?.dispose()')
    expect(home).toContain('nodeMaterial?.dispose()')
    expect(home).toContain('lineGeometry?.dispose()')
    expect(home).toContain('lineMaterial?.dispose()')
    expect(home).toContain('renderer?.forceContextLoss()')
    expect(home).toContain('renderer?.dispose()')

    const garden = readFileSync(resolve(app, 'components/garden/GardenCanvas.client.vue'), 'utf8')
    expect(garden).toContain('simulation?.stop()')
    expect(garden).toContain("simulation?.on('tick', null)")
    expect(garden).toContain('cancelAnimationFrame(frame)')
    expect(garden).toContain('resizeObserver?.disconnect()')
    expect(garden).toContain('const renderSize =')
    expect(garden).toContain('const palette =')
    const draw = garden.slice(garden.indexOf('function draw()'), garden.indexOf('function screenPoint'))
    expect(draw).not.toContain('measure()')
    expect(draw).not.toContain('getComputedStyle(')

    const music = readFileSync(resolve(app, 'components/music/MusicConsole.client.vue'), 'utf8')
    expect(music).toContain('stopSpectrumLoop()')
    expect(music).toContain('source?.disconnect()')
    expect(music).toContain('analyser.value?.disconnect()')
    expect(music).toContain('context.close()')
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
    expect(error).toContain("import('./components/error/ErrorGameCenter.client.vue')")
    expect(error).toContain('game-launcher')
    expect(error).toContain('gamesAvailable')
    expect(error).toContain('retry-button')
    expect(error).toContain('v-else class="retry-button"')
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
