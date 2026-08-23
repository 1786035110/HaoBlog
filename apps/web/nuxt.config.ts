const themeBootstrap = `(() => {
  try {
    const stored = window.localStorage.getItem('haoblog-theme')
    const theme = stored === 'blueprint' || stored === 'night' ? stored : 'night'
    document.documentElement.setAttribute('data-theme', theme)
  } catch (_) {
    document.documentElement.setAttribute('data-theme', 'night')
  }
})()`

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@unocss/nuxt'],
  css: ['~/assets/styles/tokens.css', '~/assets/styles/shell.css', '~/assets/styles/articles.css'],
  app: {
    head: {
      // 主题属性由早期脚本和客户端主题状态共同管理，避免 head manager 在 hydration 后覆盖本地主题。
      script: [{ innerHTML: themeBootstrap }],
    },
  },
  runtimeConfig: {
    apiBaseUrl: process.env.NUXT_API_BASE_URL || 'http://api:8080',
    public: {
      apiBase: '/',
    },
  },
  routeRules: {
    '/studio': { ssr: false, headers: { 'x-robots-tag': 'noindex, nofollow' } },
    '/studio/**': { ssr: false, headers: { 'x-robots-tag': 'noindex, nofollow' } },
    // 阅读页按请求头输出 Save-Data 降级结果，不能使用忽略请求头的整页缓存。
    '/articles': { cache: false },
    '/articles/**': { cache: false },
    '/article-previews/**': { cache: false, headers: { 'cache-control': 'no-store', 'referrer-policy': 'no-referrer', 'x-robots-tag': 'noindex, nofollow' } },
  },
  nitro: {
    compressPublicAssets: true,
    devProxy: {
      '/api/': {
        target: `${process.env.NUXT_API_BASE_URL || 'http://localhost:8080'}/api/`,
        changeOrigin: true,
      },
    },
  },
})
