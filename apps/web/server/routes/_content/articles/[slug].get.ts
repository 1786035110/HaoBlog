import { fetchPublicArticleContent } from '../../../utils/publicArticleContent'
import { sendProblem } from '../../../utils/proxyUpstream'

export default defineEventHandler(async (event) => {
  const slug = getRouterParam(event, 'slug') || ''
  if (!slug || slug.length > 160 || slug.includes('/')) {
    return sendProblem(event, 404, 'ARTICLE_NOT_FOUND', 'Article not found', 'The requested article does not exist')
  }

  const disconnected = new AbortController()
  const onClose = () => { if (!event.node.res.writableEnded) disconnected.abort() }
  event.node.res.once('close', onClose)
  const result = await fetchPublicArticleContent(
    slug,
    useRuntimeConfig(event).apiBaseUrl,
    getRequestHeader(event, 'if-none-match'),
    fetch,
    getRequestHeader(event, 'save-data')?.toLowerCase() === 'on',
    disconnected.signal,
    !getRequestHeader(event, 'cookie'),
  ).finally(() => event.node.res.off('close', onClose))
  setResponseHeader(event, 'vary', 'Save-Data')
  for (const name of ['etag', 'cache-control']) {
    const value = result.headers.get(name)
    if (value) setResponseHeader(event, name, value)
  }
  if (result.status === 304) {
    setResponseStatus(event, 304)
    return null
  }
  if (result.status === 404) {
    return sendProblem(event, 404, 'ARTICLE_NOT_FOUND', 'Article not found', 'The requested article does not exist')
  }
  if (result.status !== 200 || !result.body) {
    const timedOut = result.status === 504
    return sendProblem(
      event,
      timedOut ? 504 : 503,
      timedOut ? 'GATEWAY_TIMEOUT' : 'UPSTREAM_UNAVAILABLE',
      timedOut ? 'Gateway timeout' : 'Upstream unavailable',
      timedOut ? 'The article service did not respond in time' : 'The article service is temporarily unavailable',
      timedOut ? undefined : 1,
    )
  }
  return result.body
})
