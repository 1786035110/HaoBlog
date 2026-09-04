export const MUSIC_MANIFEST_VERSION = 1
export const MUSIC_MANIFEST_MAX_BYTES = 64 * 1024
export const MUSIC_TRACK_LIMIT = 20

export interface MusicTrack {
  id: string
  title: string
  artist: string
  audioUrl: string
  licenseName: string
  licenseUrl?: string
  sourceUrl?: string
}

export interface MusicManifest {
  version: 1
  tracks: MusicTrack[]
}

export function isLocalMusicHost(hostname: string) {
  return ['localhost', '127.0.0.1', '::1', '[::1]'].includes(hostname.toLowerCase())
}

function text(value: unknown, maxLength: number) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength
}

function safeUrl(value: unknown, allowLocalhost: boolean) {
  if (typeof value !== 'string' || value.length > 2048) return null
  try {
    const url = new URL(value)
    const localHttp = allowLocalhost && url.protocol === 'http:' && isLocalMusicHost(url.hostname)
    if (!(url.protocol === 'https:' || localHttp) || url.username || url.password || url.hash) return null
    return url.toString()
  } catch {
    return null
  }
}

export function emptyMusicManifest(): MusicManifest {
  return { version: MUSIC_MANIFEST_VERSION, tracks: [] }
}

export function validateMusicManifest(value: unknown, options: { allowLocalhost?: boolean } = {}): MusicManifest {
  const allowLocalhost = options.allowLocalhost === true
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('音乐清单必须是 JSON 对象。')
  const input = value as { version?: unknown; tracks?: unknown }
  if (input.version !== MUSIC_MANIFEST_VERSION) throw new Error('音乐清单版本必须为 1。')
  if (!Array.isArray(input.tracks) || input.tracks.length > MUSIC_TRACK_LIMIT) {
    throw new Error(`音乐清单最多包含 ${MUSIC_TRACK_LIMIT} 首曲目。`)
  }

  const ids = new Set<string>()
  const tracks = input.tracks.map((item, index) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error(`第 ${index + 1} 首曲目格式无效。`)
    const track = item as Record<string, unknown>
    if (!text(track.id, 128) || !text(track.title, 200) || !text(track.artist, 200) || !text(track.licenseName, 200)) {
      throw new Error(`第 ${index + 1} 首曲目缺少必填字段。`)
    }
    const id = String(track.id).trim()
    if (ids.has(id)) throw new Error(`曲目 ID 重复：${id}`)
    ids.add(id)
    const audioUrl = safeUrl(track.audioUrl, allowLocalhost)
    if (!audioUrl) throw new Error(`第 ${index + 1} 首曲目必须使用 HTTPS 音频地址。`)

    const result: MusicTrack = {
      id,
      title: String(track.title).trim(),
      artist: String(track.artist).trim(),
      audioUrl,
      licenseName: String(track.licenseName).trim(),
    }
    for (const field of ['licenseUrl', 'sourceUrl'] as const) {
      if (track[field] === undefined || track[field] === null || track[field] === '') continue
      const url = safeUrl(track[field], allowLocalhost)
      if (!url) throw new Error(`第 ${index + 1} 首曲目的 ${field} 地址无效。`)
      result[field] = url
    }
    return result
  })

  return { version: MUSIC_MANIFEST_VERSION, tracks }
}

export function decodeMusicManifest(bytes: ArrayBuffer, options: { allowLocalhost?: boolean } = {}) {
  if (bytes.byteLength > MUSIC_MANIFEST_MAX_BYTES) throw new Error('音乐清单不能超过 64 KiB。')
  let json: unknown
  try {
    json = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
  } catch {
    throw new Error('音乐清单不是有效的 UTF-8 JSON。')
  }
  return validateMusicManifest(json, options)
}
