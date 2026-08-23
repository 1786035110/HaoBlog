export default defineEventHandler((event) => {
  const path = getRouterParam(event, 'path') || ''
  const target = new URL(`/api/${path}`, useRuntimeConfig(event).apiBaseUrl)
  target.search = getRequestURL(event).search
  return proxyRequest(event, target.toString())
})
