export const MEDIA_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
export type MediaMimeType = typeof MEDIA_MIME_TYPES[number]

export const MEDIA_LIMITS = {
  inputBytes: 20 * 1024 * 1024,
  pixels: 40 * 1024 * 1024,
  outputBytes: 5 * 1024 * 1024,
  dimension: 2560,
  quality: 0.82,
} as const

export type PreparedImage = {
  blob: Blob
  mimeType: MediaMimeType
  sizeBytes: number
  width: number
  height: number
  sha256: string
}

type ImageEnvironment = {
  createImageBitmap: typeof globalThis.createImageBitmap
  createCanvas: () => HTMLCanvasElement
  digest: (data: ArrayBuffer) => Promise<ArrayBuffer>
}

const defaultEnvironment = (): ImageEnvironment => ({
  createImageBitmap: globalThis.createImageBitmap,
  createCanvas: () => document.createElement('canvas'),
  digest: data => globalThis.crypto.subtle.digest('SHA-256', data),
})

function hex(buffer: ArrayBuffer) {
  return Array.from(new Uint8Array(buffer), byte => byte.toString(16).padStart(2, '0')).join('')
}

function hasPngAnimation(bytes: Uint8Array) {
  const marker = new TextDecoder().decode(bytes)
  return marker.includes('acTL')
}

function hasWebpAnimation(bytes: Uint8Array) {
  const marker = new TextDecoder().decode(bytes)
  return marker.includes('ANIM') || marker.includes('ANMF')
}

async function validateInput(file: File) {
  if (!MEDIA_MIME_TYPES.includes(file.type as MediaMimeType)) throw new Error('仅支持 JPEG、PNG 或 WebP 图片')
  if (file.size <= 0 || file.size > MEDIA_LIMITS.inputBytes) throw new Error('原始图片大小必须在 20 MB 以内')
  const bytes = new Uint8Array(await file.slice(0, Math.min(file.size, 2 * 1024 * 1024)).arrayBuffer())
  if (file.type === 'image/png' && (bytes.length < 8 || !bytes.slice(0, 8).every((byte, index) => byte === [137, 80, 78, 71, 13, 10, 26, 10][index]) || hasPngAnimation(bytes))) throw new Error('PNG 文件签名或动画标记无效')
  if (file.type === 'image/jpeg' && !(bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff)) throw new Error('JPEG 文件签名无效')
  if (file.type === 'image/webp' && !(new TextDecoder().decode(bytes.slice(0, 4)) === 'RIFF' && new TextDecoder().decode(bytes.slice(8, 12)) === 'WEBP' && !hasWebpAnimation(bytes))) throw new Error('WebP 文件签名或动画标记无效')
}

export async function processImage(file: File, environment: Partial<ImageEnvironment> = {}): Promise<PreparedImage> {
  await validateInput(file)
  const env = { ...defaultEnvironment(), ...environment }
  const bitmap = await env.createImageBitmap(file)
  try {
    const sourceWidth = bitmap.width
    const sourceHeight = bitmap.height
    if (!sourceWidth || !sourceHeight || sourceWidth * sourceHeight > MEDIA_LIMITS.pixels) throw new Error('图片像素数不能超过 40 MP')
    const scale = Math.min(1, MEDIA_LIMITS.dimension / Math.max(sourceWidth, sourceHeight))
    const width = Math.max(1, Math.round(sourceWidth * scale))
    const height = Math.max(1, Math.round(sourceHeight * scale))
    const canvas = env.createCanvas()
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('浏览器不支持 Canvas 图片处理')
    context.drawImage(bitmap, 0, 0, width, height)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('图片压缩失败')), file.type, MEDIA_LIMITS.quality))
    if (blob.size <= 0 || blob.size > MEDIA_LIMITS.outputBytes) throw new Error('压缩后的图片必须在 5 MB 以内')
    return { blob, mimeType: file.type as MediaMimeType, sizeBytes: blob.size, width, height, sha256: hex(await env.digest(await blob.arrayBuffer())) }
  } finally {
    bitmap.close()
  }
}
