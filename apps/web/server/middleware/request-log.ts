import { randomUUID } from 'node:crypto'

const VALID_TRACE = /^[A-Za-z0-9._-]{1,64}$/
const lastErrorLog = new Map<string, number>()

function routeClass(path: string) {
  if (path.startsWith('/api/v1/admin/')) return 'admin_api'
  if (path.startsWith('/api/')) return 'public_api'
  if (path.startsWith('/_content/articles/')) return 'article_content'
  if (path.startsWith('/studio')) return 'studio'
  if (path.startsWith('/_nuxt/') || path === '/manifest.webmanifest') return 'static'
  if (path === '/rss.xml' || path === '/sitemap.xml') return 'feed'
  return 'public_page'
}

export default defineEventHandler((event) => {
  const candidate = getRequestHeader(event, 'x-request-id')
  const traceId = candidate && VALID_TRACE.test(candidate) ? candidate : randomUUID()
  const route = routeClass(event.path)
  const started = performance.now()
  setResponseHeader(event, 'x-request-id', traceId)
  event.context.haoblogTraceId = traceId
  event.node.res.once('finish', () => {
    const status = event.node.res.statusCode
    const code = String(event.context.haoblogErrorCode || '-')
    const key = `${route}:${status}:${code}`
    const now = Date.now()
    if (status >= 500 && now - (lastErrorLog.get(key) || 0) < 10_000) return
    if (status >= 500) lastErrorLog.set(key, now)
    console.info(JSON.stringify({ level: 'info', event: 'request', traceId, route, method: event.method, status, durationMs: Math.round(performance.now() - started), code }))
  })
})
