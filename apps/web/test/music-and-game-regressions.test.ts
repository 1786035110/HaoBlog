import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MusicConsole from '../app/components/music/MusicConsole.client.vue'
import SnakeGame from '../app/components/error/SnakeGame.client.vue'
import Game2048 from '../app/components/error/Game2048.client.vue'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  localStorage.clear()
  document.body.replaceChildren()
})

describe('音乐音量回归', () => {
  it.each([
    [null, .72], ['', .72], ['0', 0], ['0.35', .35], ['invalid', .72], ['2', .72],
  ])('缓存为 %s 时音量为 %s', async (stored, expected) => {
    if (stored !== null) localStorage.setItem('haoblog-music-volume', stored)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      arrayBuffer: async () => new TextEncoder().encode(JSON.stringify({ version: 1, tracks: [{
        id: 'test', title: '测试曲目', artist: '测试', licenseName: '测试授权', audioUrl: 'https://example.test/audio.mp3',
      }] })).buffer,
    }))
    const wrapper = mount(MusicConsole, { props: { open: false, manifestUrl: 'https://example.test/music.json' } })
    try {
      await flushPromises()
      expect((wrapper.get('input[aria-label="音量"]').element as HTMLInputElement).value).toBe(String(expected))
    } finally { wrapper.unmount() }
  })
})

describe.each([['贪吃蛇', SnakeGame], ['2048', Game2048]] as const)('%s 键盘回归', (_, component) => {
  it('不抢占输入框、下拉框和可编辑区域的按键，仍响应普通游戏按键', () => {
    const wrapper = mount(component, { attachTo: document.body })
    try {
      const targets = [document.createElement('input'), document.createElement('textarea'), document.createElement('select'), document.createElement('div')]
      targets[0]!.setAttribute('type', 'search')
      targets[3]!.setAttribute('contenteditable', 'true')
      for (const target of targets) {
        document.body.append(target)
        for (const key of ['w', 'a', 's', 'd', 'ArrowLeft', 'ArrowRight']) {
          const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
          target.dispatchEvent(event)
          expect(event.defaultPrevented, `${target.tagName}: ${key}`).toBe(false)
        }
        target.remove()
      }
      const gameKey = new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true })
      wrapper.element.dispatchEvent(gameKey)
      expect(gameKey.defaultPrevented).toBe(true)
    } finally { wrapper.unmount() }
    const afterUnmount = new KeyboardEvent('keydown', { key: 'ArrowUp', cancelable: true })
    window.dispatchEvent(afterUnmount)
    expect(afterUnmount.defaultPrevented).toBe(false)
  })
})
