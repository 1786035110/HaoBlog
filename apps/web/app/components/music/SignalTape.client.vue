<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { decodeMusicManifest, type MusicManifest, type MusicTrack } from '../../utils/musicManifest'
import { useDataSaver } from '../../composables/useDataSaver'
import { useMotionPreference } from '../../composables/useMotionPreference'

const { enabled: dataSaver } = useDataSaver()
const { reduced } = useMotionPreference()
const audio = ref<HTMLAudioElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const manifest = ref<MusicManifest | null>(null)
const selectedTrackId = ref('')
const volume = ref(readNumber('haoblog-music-volume', .72))
const expanded = ref(true)
const playing = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const spectrumMessage = ref('频谱待用户播放后接入。')
const spectrumEnabled = ref(true)
const pageVisible = ref(true)
let context: AudioContext | null = null
let source: MediaElementAudioSourceNode | null = null
const analyser = shallowRef<AnalyserNode | null>(null)
let raf = 0
let visibilityListener: (() => void) | null = null

const tracks = computed(() => manifest.value?.tracks || [])
const currentTrack = computed<MusicTrack | null>(() => tracks.value.find(track => track.id === selectedTrackId.value) || tracks.value[0] || null)
const currentIndex = computed(() => currentTrack.value ? tracks.value.findIndex(track => track.id === currentTrack.value?.id) : -1)
const hasSpectrum = computed(() => Boolean(analyser.value) && spectrumEnabled.value && !dataSaver.value && !reduced.value)

onMounted(async () => {
  pageVisible.value = document.visibilityState === 'visible'
  visibilityListener = () => {
    pageVisible.value = document.visibilityState === 'visible'
    syncSpectrumLoop()
  }
  document.addEventListener('visibilitychange', visibilityListener)
  const savedTrack = readString('haoblog-music-track')
  try {
    const response = await fetch('/music-manifest.json', { cache: 'no-store', headers: { accept: 'application/json' } })
    if (!response.ok) throw new Error('清单请求失败。')
    const bytes = await response.arrayBuffer()
    manifest.value = decodeMusicManifest(bytes, { allowLocalhost: location.hostname === 'localhost' || location.hostname === '127.0.0.1' })
    selectedTrackId.value = manifest.value.tracks.some(track => track.id === savedTrack) ? savedTrack : manifest.value.tracks[0]?.id || ''
    if (!manifest.value.tracks.length) errorMessage.value = '当前没有可播放的授权曲目。'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '音乐清单暂时不可用。'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (visibilityListener) document.removeEventListener('visibilitychange', visibilityListener)
  stopSpectrumLoop()
  releaseAudioGraph()
  const element = audio.value
  if (element) {
    element.pause()
    element.removeAttribute('src')
    element.load()
  }
})

watch(volume, value => {
  const normalized = Math.min(1, Math.max(0, value))
  if (audio.value) audio.value.volume = normalized
  writeString('haoblog-music-volume', String(normalized))
})

watch([expanded, playing, pageVisible, hasSpectrum], syncSpectrumLoop)

function readString(key: string) {
  try { return localStorage.getItem(key) || '' } catch { return '' }
}

function readNumber(key: string, fallback: number) {
  const value = Number(readString(key))
  return Number.isFinite(value) && value >= 0 && value <= 1 ? value : fallback
}

function writeString(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* 本地存储不可用时保持内存状态。 */ }
}

function selectTrack(track: MusicTrack) {
  selectedTrackId.value = track.id
  writeString('haoblog-music-track', track.id)
  if (playing.value) void playTrack(track)
}

function moveTrack(step: number) {
  if (!tracks.value.length) return
  const index = (currentIndex.value + step + tracks.value.length) % tracks.value.length
  const track = tracks.value[index]
  if (track) selectTrack(track)
}

async function playTrack(track = currentTrack.value) {
  const element = audio.value
  if (!element || !track) return
  selectedTrackId.value = track.id
  writeString('haoblog-music-track', track.id)
  try {
    if (element.dataset.trackId !== track.id) {
      element.dataset.trackId = track.id
      element.src = track.audioUrl
      element.load()
    }
    element.volume = volume.value
    await element.play()
    playing.value = true
    await ensureAudioGraph()
    spectrumMessage.value = analyser.value ? '频谱已接入；暂停、折叠或离开页面时自动停止绘制。' : spectrumMessage.value
    syncSpectrumLoop()
  } catch {
    playing.value = false
    errorMessage.value = '浏览器未允许播放或音频地址不可用；请检查 CORS 后重试。'
    stopSpectrumLoop()
  }
}

function pauseTrack() {
  audio.value?.pause()
  playing.value = false
  stopSpectrumLoop()
}

async function togglePlay() {
  if (playing.value) pauseTrack()
  else await playTrack()
}

function handleEnded() {
  playing.value = false
  moveTrack(1)
  stopSpectrumLoop()
}

function handleAudioError() {
  playing.value = false
  errorMessage.value = '音频加载失败；播放器仍保留曲目信息和授权链接。'
  stopSpectrumLoop()
}

async function ensureAudioGraph() {
  if (!audio.value || context || dataSaver.value || reduced.value || !spectrumEnabled.value) return
  if (!('AudioContext' in window) || !('AnalyserNode' in window)) {
    spectrumMessage.value = '当前浏览器不支持 Web Audio，已保留普通播放。'
    spectrumEnabled.value = false
    return
  }
  try {
    context = new AudioContext()
    source = context.createMediaElementSource(audio.value)
    analyser.value = context.createAnalyser()
    analyser.value.fftSize = 64
    analyser.value.smoothingTimeConstant = .75
    source.connect(analyser.value)
    analyser.value.connect(context.destination)
    if (context.state === 'suspended') await context.resume()
  } catch {
    releaseAudioGraph()
    spectrumEnabled.value = false
    spectrumMessage.value = '频谱接入失败，已保留普通播放。'
  }
}

function syncSpectrumLoop() {
  const shouldRun = playing.value && pageVisible.value && expanded.value && hasSpectrum.value
  if (shouldRun && !raf) raf = requestAnimationFrame(drawSpectrum)
  if (!shouldRun) stopSpectrumLoop()
}

function stopSpectrumLoop() {
  if (raf) cancelAnimationFrame(raf)
  raf = 0
}

function drawSpectrum() {
  raf = 0
  if (!analyser.value || !canvas.value || !playing.value || !pageVisible.value || !expanded.value || !hasSpectrum.value) return
  const element = canvas.value
  const context2d = element.getContext('2d')
  if (!context2d) {
    spectrumEnabled.value = false
    spectrumMessage.value = 'Canvas 不可用，已保留普通播放。'
    return
  }
  const width = Math.max(1, Math.floor(element.clientWidth * Math.min(window.devicePixelRatio || 1, 1.5)))
  const height = Math.max(1, Math.floor(element.clientHeight * Math.min(window.devicePixelRatio || 1, 1.5)))
  if (element.width !== width || element.height !== height) { element.width = width; element.height = height }
  const values = new Uint8Array(analyser.value.frequencyBinCount)
  analyser.value.getByteFrequencyData(values)
  context2d.clearRect(0, 0, width, height)
  context2d.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--color-accent').trim() || '#a8ff60'
  const gap = Math.max(1, width / values.length * .18)
  const barWidth = Math.max(1, width / values.length - gap)
  values.forEach((value, index) => {
    const barHeight = Math.max(1, value / 255 * height)
    context2d.fillRect(index * (barWidth + gap), height - barHeight, barWidth, barHeight)
  })
  raf = requestAnimationFrame(drawSpectrum)
}

function releaseAudioGraph() {
  source?.disconnect()
  analyser.value?.disconnect()
  source = null
  analyser.value = null
  if (context) void context.close().catch(() => undefined)
  context = null
}
</script>

<template>
  <section class="signal-tape" aria-label="信号磁带播放器">
    <div class="signal-tape-bar">
      <span class="instrument-label">SIGNAL TAPE / MUSIC</span>
      <span class="signal-tape-state" role="status" aria-live="polite">{{ playing ? 'PLAYING' : 'READY' }}</span>
      <button class="tape-toggle" type="button" :aria-expanded="expanded" @click="expanded = !expanded">{{ expanded ? '折叠' : '展开' }}</button>
    </div>
    <div v-show="expanded" class="signal-tape-panel">
      <p v-if="loading" class="tape-note" role="status">正在读取授权清单…</p>
      <p v-else-if="errorMessage && !currentTrack" class="tape-note tape-note--warn" role="alert">{{ errorMessage }}</p>
      <template v-if="currentTrack">
        <div class="tape-meta">
          <div>
            <p class="tape-track-title">{{ currentTrack.title }}</p>
            <p class="tape-track-artist">{{ currentTrack.artist }}</p>
          </div>
          <p class="tape-license">授权：{{ currentTrack.licenseName }}<br><a v-if="currentTrack.licenseUrl" :href="currentTrack.licenseUrl" target="_blank" rel="noopener noreferrer">授权链接 ↗</a><a v-if="currentTrack.sourceUrl" :href="currentTrack.sourceUrl" target="_blank" rel="noopener noreferrer">来源链接 ↗</a></p>
        </div>
        <canvas v-show="hasSpectrum" ref="canvas" class="tape-spectrum" aria-label="当前音频频谱" />
        <p class="tape-note" role="status">{{ spectrumMessage }}</p>
        <div class="tape-controls">
          <button type="button" aria-label="上一首" :disabled="!tracks.length" @click="moveTrack(-1)">PREV</button>
          <button type="button" class="tape-play" :aria-label="playing ? '暂停播放' : '开始播放'" :disabled="!tracks.length" @click="togglePlay">{{ playing ? 'PAUSE' : 'PLAY' }}</button>
          <button type="button" aria-label="下一首" :disabled="!tracks.length" @click="moveTrack(1)">NEXT</button>
          <label class="tape-volume">音量 <input v-model.number="volume" type="range" min="0" max="1" step=".01" aria-label="音量"></label>
          <button type="button" :aria-pressed="spectrumEnabled" :disabled="Boolean(dataSaver || reduced)" @click="spectrumEnabled = !spectrumEnabled; syncSpectrumLoop()">{{ spectrumEnabled ? 'SPECTRUM ON' : 'SPECTRUM OFF' }}</button>
        </div>
      </template>
      <p v-if="errorMessage && currentTrack" class="tape-note tape-note--warn" role="alert">{{ errorMessage }}</p>
      <audio ref="audio" crossorigin="anonymous" preload="none" @play="playing = true" @pause="playing = false" @ended="handleEnded" @error="handleAudioError" />
    </div>
  </section>
</template>

<style scoped>
.signal-tape { position: relative; z-index: 2; width: min(58rem, calc(100% - 2 * var(--space-shell))); margin: 0 auto 1rem; border: 1px solid var(--color-border); background: var(--color-bg-sub); color: var(--color-text-main); }
.signal-tape-bar, .signal-tape-panel { padding: .65rem .8rem; }
.signal-tape-bar { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: .8rem; }
.signal-tape-state, .tape-toggle, .tape-note, .tape-track-artist, .tape-license, .tape-controls { font: var(--text-xs)/1.4 var(--font-mono); }
.signal-tape-state, .tape-track-artist, .tape-license, .tape-note { color: var(--color-text-muted); }
.tape-toggle, .tape-controls button { border: 1px solid var(--color-border); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1.3 var(--font-mono); }
.tape-toggle { padding: .25rem .45rem; }
.signal-tape-panel { border-top: 1px solid var(--color-border); }
.tape-meta { display: flex; justify-content: space-between; gap: 1rem; }
.tape-track-title { margin: 0; color: var(--color-text-main); font: 700 var(--text-lg)/1.2 var(--font-display); }
.tape-track-artist, .tape-license { margin: .3rem 0 0; }
.tape-license { text-align: right; }
.tape-license a { display: inline-block; margin-left: .6rem; color: var(--color-accent); }
.tape-spectrum { display: block; width: 100%; height: 3.5rem; margin: .8rem 0 .3rem; border-bottom: 1px solid var(--color-border); }
.tape-controls { display: flex; flex-wrap: wrap; align-items: center; gap: .45rem; margin-top: .7rem; }
.tape-controls button { padding: .45rem .55rem; }
.tape-controls button:hover:not(:disabled), .tape-controls button:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
.tape-play { border-color: var(--color-accent) !important; }
.tape-controls button:disabled { cursor: not-allowed; opacity: .5; }
.tape-volume { display: inline-flex; align-items: center; gap: .4rem; margin-left: auto; color: var(--color-text-muted); }
.tape-volume input { accent-color: var(--color-accent); }
.tape-note { margin: .6rem 0 0; }
.tape-note--warn { color: var(--color-warn); }
@media (max-width: 640px) { .signal-tape { width: calc(100% - 1.5rem); }.tape-meta { flex-direction: column; }.tape-license { text-align: left; }.tape-volume { margin-left: 0; width: 100%; } }
@media (prefers-reduced-motion: reduce) { .tape-spectrum { display: none; } }
</style>
