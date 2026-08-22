export default defineEventHandler((event) => {
  const target = new URL('/rss.xml', useRuntimeConfig(event).apiBaseUrl)
  return proxyRequest(event, target.toString())
})
