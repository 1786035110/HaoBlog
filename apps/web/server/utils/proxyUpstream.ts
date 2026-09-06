import { randomUUID } from 'node:crypto'
import type { H3Event } from 'h3'

const TRACE_ID = /^[A-Za-z0-9._-]{1,64}$/

export async function proxyUpstream(event: H3Event, target: URL, timeoutMs: number) {
  const disconnected = new AbortController()
  const timeout = AbortSignal.timeout(timeoutMs)
  const onClose = () => { if (!event.node.res.writableEnded) disconnected.abort() }
  event.node.res.once('close', onClose)
  try {
    return await proxyRequest(event, target.toString(), {
      streamRequest: true,
      sendStream: true,
      fetchOptions: { signal: AbortSignal.any([disconnected.signal, timeout]) },
    })
  } catch {
    if (disconnected.signal.aborted) return null
    const timedOut = timeout.aborted
    return sendProblem(
      event,
      timedOut ? 504 : 503,
      timedOut ? 'GATEWAY_TIMEOUT' : 'UPSTREAM_UNAVAILABLE',
      timedOut ? 'Gateway timeout' : 'Upstream unavailable',
      timedOut ? 'The upstream service did not respond in time' : 'The upstream service is temporarily unavailable',
      timedOut ? undefined : 1,
    )
  } finally {
    event.node.res.off('close', onClose)
  }
}

export function sendProblem(event: H3Event, status: number, code: string, title: string, detail: string, retryAfter?: number) {
  const candidate = getRequestHeader(event, 'x-request-id')
  const traceId = candidate && TRACE_ID.test(candidate) ? candidate : randomUUID()
  event.context.haoblogErrorCode = code
  setResponseStatus(event, status)
  setResponseHeader(event, 'content-type', 'application/problem+json')
  setResponseHeader(event, 'cache-control', 'no-store')
  setResponseHeader(event, 'x-request-id', traceId)
  if (retryAfter) setResponseHeader(event, 'retry-after', retryAfter)
  return { code, title, detail, traceId }
}
