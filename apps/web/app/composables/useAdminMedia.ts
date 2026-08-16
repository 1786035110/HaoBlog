import type { components } from '@haoblog/api-client'
import { ref } from 'vue'
import { processImage, type PreparedImage } from '../utils/mediaUpload'
import { useAdminSession } from './useAdminSession'

type UploadGrant = components['schemas']['MediaUploadResponse']
type MediaAsset = components['schemas']['MediaAssetResponse']

export function useAdminMedia() {
  const session = useAdminSession()
  const phase = ref<'idle' | 'processing' | 'uploading' | 'confirming' | 'error' | 'done'>('idle')
  const asset = ref<MediaAsset | null>(null)
  const error = ref('')
  const prepared = ref<PreparedImage | null>(null)
  const grant = ref<UploadGrant | null>(null)

  async function createGrant(image: PreparedImage) {
    return session.write<UploadGrant>('/api/v1/admin/media/uploads', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mimeType: image.mimeType, sizeBytes: image.sizeBytes, width: image.width, height: image.height, sha256: image.sha256 }),
    })
  }

  async function putToCos(currentGrant: UploadGrant, image: PreparedImage) {
    const body = new FormData()
    for (const [key, value] of Object.entries(currentGrant.fields)) body.append(key, value)
    body.append('file', image.blob, 'image')
    const response = await fetch(currentGrant.uploadUrl, { method: 'POST', body })
    if (!response.ok) throw new Error('对象存储上传失败')
  }

  async function confirm(currentGrant: UploadGrant) {
    return session.write<MediaAsset>(`/api/v1/admin/media/uploads/${currentGrant.uploadId}/complete`, { method: 'POST' })
  }

  async function upload(file: File) {
    phase.value = 'processing'
    error.value = ''
    asset.value = null
    prepared.value = null
    grant.value = null
    try {
      const image = await processImage(file)
      prepared.value = image
      phase.value = 'uploading'
      grant.value = await createGrant(image)
      await putToCos(grant.value, image)
      phase.value = 'confirming'
      asset.value = await confirm(grant.value)
      phase.value = 'done'
      return asset.value
    } catch (cause) {
      phase.value = 'error'
      error.value = cause instanceof Error ? cause.message : '媒体上传失败'
      asset.value = null
      return null
    }
  }

  async function retry() {
    if (!prepared.value) return null
    error.value = ''
    try {
      if (!grant.value) grant.value = await createGrant(prepared.value)
      phase.value = 'uploading'
      await putToCos(grant.value, prepared.value)
      phase.value = 'confirming'
      asset.value = await confirm(grant.value)
      phase.value = 'done'
      return asset.value
    } catch (cause) {
      phase.value = 'error'
      error.value = cause instanceof Error ? cause.message : '媒体上传失败'
      asset.value = null
      return null
    }
  }

  function reset() {
    phase.value = 'idle'
    error.value = ''
    prepared.value = null
    grant.value = null
    asset.value = null
  }

  return { phase, asset, error, prepared, grant, upload, retry, reset }
}
