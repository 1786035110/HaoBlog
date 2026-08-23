import { setHeader } from 'h3'
import { decodeMusicManifest, emptyMusicManifest, MUSIC_MANIFEST_MAX_BYTES } from '../../app/utils/musicManifest'

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1'])

function configuredManifestUrl() {
  return String(process.env.HAOBLOG_MUSIC_MANIFEST_URL || '').trim()
}

function allowLocalhost() {
  return process.env.NODE_ENV !== 'production' || process.env.HAOBLOG_PWA_TEST === 'true'
}

function isAllowedSource(value: string) {
  try {
    const url = new URL(value)
    const localHttp = allowLocalhost()
      && url.protocol === 'http:'
      && LOCAL_HOSTS.has(url.hostname.toLowerCase())
    return Boolean(url.host && !url.username && !url.password && !url.hash && (url.protocol === 'https:' || localHttp))
  } catch {
    return false
  }
}

export default defineEventHandler(async (event) => {
  setHeader(event, 'Cache-Control', 'no-store')
  setHeader(event, 'X-Content-Type-Options', 'nosniff')
  const source = configuredManifestUrl()
  if (!source || !isAllowedSource(source)) return emptyMusicManifest()

  try {
    const response = await fetch(source, {
      headers: { accept: 'application/json' },
      redirect: 'error',
      signal: AbortSignal.timeout(5000),
    })
    const declaredLength = Number(response.headers.get('content-length') || 0)
    if (declaredLength > MUSIC_MANIFEST_MAX_BYTES) throw new Error('manifest-too-large')
    if (!response.ok) throw new Error(`manifest-${response.status}`)
    const bytes = await response.arrayBuffer()
      return decodeMusicManifest(bytes, { allowLocalhost: allowLocalhost() })
  } catch {
    return emptyMusicManifest()
  }
})
