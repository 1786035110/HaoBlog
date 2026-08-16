export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@unocss/nuxt'],
  css: ['~/assets/styles/tokens.css', '~/assets/styles/shell.css', '~/assets/styles/articles.css'],
  app: { head: { htmlAttrs: { 'data-theme': 'night' } } },
  runtimeConfig: {
    apiBaseUrl: process.env.NUXT_API_BASE_URL || 'http://api:8080',
    public: {
      apiBase: '/',
    },
  },
  routeRules: {
    '/studio': { ssr: false },
    '/studio/**': { ssr: false },
    '/articles': { cache: { maxAge: 60, swr: false } },
    '/articles/**': { cache: { maxAge: 60, swr: false } },
  },
  nitro: {
    devProxy: {
      '/api/': {
        target: `${process.env.NUXT_API_BASE_URL || 'http://localhost:8080'}/api/`,
        changeOrigin: true,
      },
    },
  },
})
