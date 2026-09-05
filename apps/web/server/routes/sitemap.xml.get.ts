import { proxyUpstream } from '../utils/proxyUpstream'

export default defineEventHandler((event) => {
  const target = new URL('/sitemap.xml', useRuntimeConfig(event).apiBaseUrl)
  return proxyUpstream(event, target, 5000)
})
