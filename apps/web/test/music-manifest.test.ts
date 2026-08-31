import { describe, expect, it } from 'vitest'
import { decodeMusicManifest, MUSIC_MANIFEST_MAX_BYTES, validateMusicManifest } from '../app/utils/musicManifest'

const track = {
  id: 'night-01',
  title: 'Night calibration',
  artist: 'Hao',
  audioUrl: 'https://cdn.example.test/night-01.mp3',
  licenseName: 'CC BY 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  sourceUrl: 'https://source.example.test/night-01',
}

describe('music manifest protocol', () => {
  it('accepts version 1 and preserves optional authorization links', () => {
    expect(validateMusicManifest({ version: 1, tracks: [track] })).toEqual({ version: 1, tracks: [track] })
  })

  it('enforces version, track count and required fields', () => {
    expect(() => validateMusicManifest({ version: 2, tracks: [] })).toThrow('版本必须为 1')
    expect(() => validateMusicManifest({ version: 1, tracks: Array.from({ length: 21 }, (_, index) => ({ ...track, id: `track-${index}` })) })).toThrow('最多包含 20')
    expect(() => validateMusicManifest({ version: 1, tracks: [{ ...track, licenseName: '' }] })).toThrow('缺少必填字段')
  })

  it('rejects non-HTTPS audio and unsafe authorization links', () => {
    expect(() => validateMusicManifest({ version: 1, tracks: [{ ...track, audioUrl: 'http://cdn.example.test/audio.mp3' }] })).toThrow('HTTPS')
    expect(() => validateMusicManifest({ version: 1, tracks: [{ ...track, licenseUrl: 'javascript:alert(1)' }] })).toThrow('licenseUrl')
  })

  it('allows localhost HTTP only for local tests', () => {
    expect(validateMusicManifest({ version: 1, tracks: [{ ...track, audioUrl: 'http://localhost:4173/audio.mp3' }] }, { allowLocalhost: true }).tracks[0]?.audioUrl).toBe('http://localhost:4173/audio.mp3')
    expect(() => validateMusicManifest({ version: 1, tracks: [{ ...track, audioUrl: 'http://localhost:4173/audio.mp3' }] })).toThrow('HTTPS')
  })

  it('rejects manifests larger than 64 KiB before parsing', () => {
    const bytes = new Uint8Array(MUSIC_MANIFEST_MAX_BYTES + 1)
    expect(() => decodeMusicManifest(bytes.buffer)).toThrow('64 KiB')
  })
})
