const CACHE_NAME = 'haoblog-tools-v1'
const TOOL_PAGE = new URL('/tools', self.location.origin).toString()
const READY_MARKER = new URL('/__haoblog_tools_ready__', self.location.origin).toString()

function isRangeRequest(request) {
  return request.headers.has('range')
}

function isToolPage(url) {
  return url.origin === self.location.origin && url.pathname === '/tools'
}

function isStaticToolAsset(url) {
  return url.origin === self.location.origin && (url.pathname.startsWith('/_nuxt/') || url.pathname.startsWith('/workers/'))
}

async function networkFirstToolPage(request) {
  const cache = await caches.open(CACHE_NAME)
  if (!(await cache.match(READY_MARKER))) return fetch(request)
  try {
    const response = await fetch(new Request(request, { cache: 'no-store' }))
    if (response.ok) await cache.put(TOOL_PAGE, response.clone())
    return response
  } catch {
    const cached = await cache.match(TOOL_PAGE)
    return cached || new Response('离线工具尚未准备完成。请联网打开 /tools 并选择“准备离线工具”。', { status: 503, headers: { 'Content-Type': 'text/plain; charset=utf-8' } })
  }
}

async function cacheFirstStatic(request) {
  const cache = await caches.open(CACHE_NAME)
  if (!(await cache.match(READY_MARKER))) return fetch(request)
  const cached = await cache.match(request)
  if (cached) return cached
  return fetch(request)
}

async function prepareTools(resourceUrls, port) {
  try {
    const cache = await caches.open(CACHE_NAME)
    const pageResponse = await fetch(new Request(TOOL_PAGE, { cache: 'no-store' }))
    if (!pageResponse.ok) throw new Error('工具目录暂时无法缓存。')
    await cache.put(TOOL_PAGE, pageResponse.clone())
    let cached = 1
    for (const raw of Array.isArray(resourceUrls) ? resourceUrls : []) {
      let url
      try { url = new URL(raw) } catch { continue }
      if (!isStaticToolAsset(url) || isRangeRequest(new Request(url))) continue
      const response = await fetch(new Request(url, { cache: 'no-store' }))
      if (!response.ok) continue
      await cache.put(url.toString(), response.clone())
      cached += 1
    }
    await cache.put(READY_MARKER, new Response('ready', { headers: { 'Cache-Control': 'no-store' } }))
    port?.postMessage({ ok: true, cached })
  } catch (error) {
    port?.postMessage({ ok: false, error: error instanceof Error ? error.message : '离线工具准备失败。' })
  }
}

self.addEventListener('install', () => {
  // 不预热、不跳过等待；首次安装和更新都由用户动作/旧页面生命周期决定。
})

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('haoblog-tools-') && key !== CACHE_NAME).map(key => caches.delete(key)))))
})

self.addEventListener('message', event => {
  if (event.data?.type === 'PREPARE_TOOLS') void prepareTools(event.data.resourceUrls, event.ports[0])
})

self.addEventListener('fetch', event => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || isRangeRequest(request)) return
  if (isToolPage(url)) {
    event.respondWith(networkFirstToolPage(request))
    return
  }
  if (isStaticToolAsset(url)) event.respondWith(cacheFirstStatic(request))
})
