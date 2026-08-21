import { fetchPublicArticleContent } from '../../../utils/publicArticleContent'

export default defineEventHandler(async (event) => {
  const slug = getRouterParam(event, 'slug') || ''
  if (!slug || slug.length > 160 || slug.includes('/')) throw createError({ statusCode: 404, statusMessage: 'Article not found' })

  const result = await fetchPublicArticleContent(
    slug,
    useRuntimeConfig(event).apiBaseUrl,
    getRequestHeader(event, 'if-none-match'),
  )
  for (const name of ['etag', 'cache-control']) {
    const value = result.headers.get(name)
    if (value) setResponseHeader(event, name, value)
  }
  if (result.status === 304) {
    setResponseStatus(event, 304)
    return null
  }
  if (result.status === 404) throw createError({ statusCode: 404, statusMessage: 'Article not found' })
  if (result.status !== 200 || !result.body) throw createError({ statusCode: 502, statusMessage: 'Article service unavailable' })
  return result.body
})
