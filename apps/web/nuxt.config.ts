export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@unocss/nuxt'],
  css: ['~/assets/styles/tokens.css', '~/assets/styles/shell.css'],
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080',
    },
  },
  routeRules: {
    '/studio/**': { ssr: false },
  },
})
