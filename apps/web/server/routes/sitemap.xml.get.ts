export default defineEventHandler((event) => {
  const target = new URL('/sitemap.xml', useRuntimeConfig(event).apiBaseUrl)
  return proxyRequest(event, target.toString())
})
