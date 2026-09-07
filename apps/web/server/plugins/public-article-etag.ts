import { createHash } from 'node:crypto'

export default defineNitroPlugin((nitroApp) => {
  nitroApp.hooks.hook('render:response', (response, { event }) => {
    if (event.method !== 'GET' || !/^\/articles\/[a-z0-9]+(?:-[a-z0-9]+)*$/.test(event.path)
      || response.statusCode && response.statusCode !== 200 || typeof response.body !== 'string') return

    const etag = `"${createHash('sha256').update(response.body).digest('base64url')}"`
    response.headers = {
      ...response.headers,
      etag,
      'cache-control': 'public, max-age=0, must-revalidate',
      vary: mergeVary(response.headers?.vary, 'Save-Data'),
    }
    if (getRequestHeader(event, 'if-none-match')?.split(',').map(value => value.trim()).includes(etag)) {
      response.statusCode = 304
      response.body = ''
    }
  })
})

function mergeVary(current: string | undefined, value: string) {
  const values = new Set((current || '').split(',').map(item => item.trim()).filter(Boolean))
  values.add(value)
  return [...values].join(', ')
}
