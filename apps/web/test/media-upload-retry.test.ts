import { beforeEach, describe, expect, it, vi } from 'vitest'

const write = vi.fn()
vi.mock('../app/composables/useAdminSession', () => ({ useAdminSession: () => ({ write }) }))
vi.mock('../app/utils/mediaUpload', () => ({ processImage: vi.fn(async () => ({
  blob: new Blob(['image'], { type: 'image/jpeg' }), mimeType: 'image/jpeg', sizeBytes: 5, width: 10, height: 10,
  sha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
})) }))

import { useAdminMedia } from '../app/composables/useAdminMedia'

describe('direct media upload retry', () => {
  beforeEach(() => {
    write.mockReset()
    vi.restoreAllMocks()
  })

  it('reuses the signed intent after a direct OSS failure and only exposes completed media', async () => {
    const grant = { uploadId: 'u1', objectKey: 'media/key.jpg', uploadUrl: 'https://oss.invalid/', fields: { key: 'media/key.jpg' }, expiresAt: '2030-01-01T00:05:00Z' }
    const asset = { id: 'm1', objectKey: grant.objectKey, publicUrl: 'https://cdn.invalid/key.jpg', mimeType: 'image/jpeg', sizeBytes: 5, width: 10, height: 10, sha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', status: 'AVAILABLE', createdAt: '', updatedAt: '' }
    write.mockResolvedValueOnce(grant).mockResolvedValueOnce(asset)
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 500 }))
      .mockResolvedValueOnce(new Response('', { status: 204 }))
    const media = useAdminMedia()
    expect(await media.upload(new File(['jpeg'], 'photo.jpg', { type: 'image/jpeg' }))).toBeNull()
    expect(media.asset.value).toBeNull()
    expect(await media.retry()).toEqual(asset)
    expect(media.phase.value).toBe('done')
    expect(write).toHaveBeenCalledTimes(2)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
