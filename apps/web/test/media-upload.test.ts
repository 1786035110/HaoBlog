import { describe, expect, it, vi } from 'vitest'
import { MEDIA_LIMITS, processImage } from '../app/utils/mediaUpload'

function canvas(output: Blob) {
  return {
    width: 0,
    height: 0,
    getContext: () => ({ drawImage: vi.fn() }),
    toBlob: (callback: BlobCallback) => callback(output),
  } as unknown as HTMLCanvasElement
}

function environment(output = new Blob(['compressed'], { type: 'image/jpeg' }), width = 4000, height = 2000) {
  return {
    createImageBitmap: vi.fn(async () => ({ width, height, close: vi.fn() })),
    createCanvas: () => canvas(output),
    digest: vi.fn(async () => new Uint8Array(32).buffer),
  }
}

describe('media image processing boundary', () => {
  it('rejects unsupported, malformed and animated inputs', async () => {
    await expect(processImage(new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }), environment() as never)).rejects.toThrow('仅支持')
    await expect(processImage(new File(['GIF89a'], 'x.gif', { type: 'image/gif' }), environment() as never)).rejects.toThrow('仅支持')
    await expect(processImage(new File(['not-jpeg'], 'x.jpg', { type: 'image/jpeg' }), environment() as never)).rejects.toThrow('签名')
    const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, ...new TextEncoder().encode('acTL')])
    await expect(processImage(new File([png], 'x.png', { type: 'image/png' }), environment() as never)).rejects.toThrow('动画')
  })

  it('resizes by longest edge, keeps exact MIME and computes SHA-256', async () => {
    const env = environment(new Blob(['encoded'], { type: 'image/jpeg' }), 4000, 2000)
    const result = await processImage(new File([new Uint8Array([255, 216, 255, 0])], 'x.jpg', { type: 'image/jpeg' }), env as never)
    expect(result.width).toBe(MEDIA_LIMITS.dimension)
    expect(result.height).toBe(1280)
    expect(result.mimeType).toBe('image/jpeg')
    expect(result.sha256).toHaveLength(64)
    expect(env.createImageBitmap).toHaveBeenCalledOnce()
  })

  it('rejects oversized source, pixel count and compressed output', async () => {
    const large = new File([new Uint8Array(MEDIA_LIMITS.inputBytes + 1)], 'x.jpg', { type: 'image/jpeg' })
    await expect(processImage(large, environment() as never)).rejects.toThrow('20 MB')
    await expect(processImage(new File([new Uint8Array([255, 216, 255])], 'x.jpg', { type: 'image/jpeg' }), environment(undefined, 10000, 5000) as never)).rejects.toThrow('40 MP')
    await expect(processImage(new File([new Uint8Array([255, 216, 255])], 'x.jpg', { type: 'image/jpeg' }), environment(new Blob([new Uint8Array(MEDIA_LIMITS.outputBytes + 1)], { type: 'image/jpeg' })) as never)).rejects.toThrow('5 MB')
  })
})
