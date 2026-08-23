import { onBeforeUnmount, onMounted, readonly, ref } from 'vue'

export type SiteTheme = 'night' | 'blueprint'

export const THEME_STORAGE_KEY = 'haoblog-theme'

export function normalizeTheme(value: unknown): SiteTheme {
  return value === 'blueprint' ? 'blueprint' : 'night'
}

export function useTheme() {
  const theme = typeof useState === 'function'
    ? useState<SiteTheme>('haoblog-theme', () => 'night')
    : ref<SiteTheme>('night')
  let transitionTimer: number | undefined

  function applyTheme(next: SiteTheme, persist = true) {
    theme.value = normalizeTheme(next)
    if (!import.meta.client) return

    const root = document.documentElement
    root.dataset.theme = theme.value
    if (persist) {
      try {
        localStorage.setItem(THEME_STORAGE_KEY, theme.value)
      } catch {
        // localStorage 不可用时保留当前会话主题。
      }
    }

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      if (transitionTimer) window.clearTimeout(transitionTimer)
      delete root.dataset.themeTransition
      return
    }
    root.dataset.themeTransition = 'on'
    if (transitionTimer) window.clearTimeout(transitionTimer)
    transitionTimer = window.setTimeout(() => {
      delete root.dataset.themeTransition
    }, 420)
  }

  function syncTheme() {
    if (!import.meta.client) return
    let stored: string | null = null
    try {
      stored = localStorage.getItem(THEME_STORAGE_KEY)
    } catch {
      stored = null
    }
    applyTheme(normalizeTheme(stored || document.documentElement.dataset.theme), false)
  }

  onMounted(syncTheme)
  onBeforeUnmount(() => {
    if (transitionTimer) window.clearTimeout(transitionTimer)
  })

  return { theme: readonly(theme), setTheme: applyTheme, syncTheme }
}
