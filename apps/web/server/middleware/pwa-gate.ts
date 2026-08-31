import { createError, getRequestURL } from 'h3'

export default defineEventHandler((event) => {
  const pathname = getRequestURL(event).pathname
  const enabled = process.env.NODE_ENV === 'production' || process.env.HAOBLOG_PWA_TEST === 'true'
  if (!enabled && (pathname === '/sw.js' || pathname === '/manifest.webmanifest')) {
    throw createError({ statusCode: 404, statusMessage: 'PWA is disabled outside production/test mode' })
  }
})
