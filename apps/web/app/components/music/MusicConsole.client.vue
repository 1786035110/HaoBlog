<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { decodeMusicManifest, type MusicManifest, type MusicTrack } from '../../utils/musicManifest'
import { useDataSaver } from '../../composables/useDataSaver'
import { useMotionPreference } from '../../composables/useMotionPreference'

const props = defineProps<{ open: boolean; manifestUrl: string }>()
const emit = defineEmits<{ open: []; close: [] }>()
const { enabled: dataSaver } = useDataSaver()
const { reduced } = useMotionPreference()
const dialog = ref<HTMLDialogElement | null>(null)
const openButton = ref<HTMLButtonElement | null>(null)
const audio = ref<HTMLAudioElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const manifest = ref<MusicManifest | null>(null)
const selectedTrackId = ref(readString('haoblog-music-track'))
const volume = ref(readNumber('haoblog-music-volume', .72))
const muted = ref(false)
const playing = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const spectrumMessage = ref('频谱会在播放后接入。')
const spectrumEnabled = ref(!dataSaver.value && !reduced.value)
const useCors = ref(spectrumEnabled.value)
const fallbackApplied = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const playMode = ref<'all' | 'one'>('all')
const pageVisible = ref(true)
let context: AudioContext | null = null
let source: MediaElementAudioSourceNode | null = null
const analyser = shallowRef<AnalyserNode | null>(null)
let raf = 0

const tracks = computed(() => manifest.value?.tracks || [])
const currentTrack = computed<MusicTrack | null>(() => tracks.value.find(track => track.id === selectedTrackId.value) || tracks.value[0] || null)
const currentIndex = computed(() => currentTrack.value ? tracks.value.findIndex(track => track.id === currentTrack.value?.id) : -1)
const hasSpectrum = computed(() => Boolean(analyser.value) && spectrumEnabled.value && props.open && pageVisible.value && !dataSaver.value && !reduced.value)

onMounted(async () => {
  pageVisible.value = document.visibilityState === 'visible'
  document.addEventListener('visibilitychange', handleVisibility)
  await loadManifest()
  syncDialog()
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibility)
  stopSpectrumLoop()
  releaseAudioGraph()
  const element = audio.value
  if (element) {
    element.pause()
    element.removeAttribute('src')
    element.load()
  }
})

watch(() => props.open, syncDialog)
watch([playing, pageVisible, hasSpectrum], syncSpectrumLoop)
watch(volume, value => {
  const normalized = Math.min(1, Math.max(0, value))
  if (audio.value) audio.value.volume = normalized
  writeString('haoblog-music-volume', String(normalized))
})

async function loadManifest() {
  try {
    if (!props.manifestUrl) throw new Error('音乐清单尚未配置。')
    const response = await fetch(props.manifestUrl, {
      cache: 'no-store', credentials: 'omit', headers: { accept: 'application/json' }, signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) throw new Error(`音乐清单请求失败（HTTP ${response.status}）。`)
    manifest.value = decodeMusicManifest(await response.arrayBuffer(), {
      allowLocalhost: location.hostname === 'localhost' || location.hostname === '127.0.0.1',
    })
    if (!tracks.value.length) throw new Error('当前没有可播放的授权曲目。')
    if (!tracks.value.some(track => track.id === selectedTrackId.value)) selectedTrackId.value = tracks.value[0]!.id
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '音乐清单暂时不可用。'
  } finally {
    loading.value = false
  }
}

function syncDialog() {
  void nextTick(() => {
    if (props.open && dialog.value && !dialog.value.open) dialog.value.showModal()
    else if (!props.open && dialog.value?.open) dialog.value.close()
  })
}

function handleDialogClose() {
  stopSpectrumLoop()
  emit('close')
  void nextTick(() => openButton.value?.focus())
}

function handleVisibility() {
  pageVisible.value = document.visibilityState === 'visible'
  syncSpectrumLoop()
}

function readString(key: string) {
  try { return localStorage.getItem(key) || '' } catch { return '' }
}

function readNumber(key: string, fallback: number) {
  const stored = readString(key).trim()
  if (!stored) return fallback
  const value = Number(stored)
  return Number.isFinite(value) && value >= 0 && value <= 1 ? value : fallback
}

function writeString(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* 本地存储不可用时保持内存状态。 */ }
}

function selectTrack(track: MusicTrack) {
  const wasPlaying = playing.value
  selectedTrackId.value = track.id
  writeString('haoblog-music-track', track.id)
  resetMediaSource()
  if (wasPlaying) void playTrack(track)
}

function moveTrack(step: number, autoplay = playing.value) {
  if (!tracks.value.length) return
  const index = (currentIndex.value + step + tracks.value.length) % tracks.value.length
  const track = tracks.value[index]
  if (!track) return
  const wasPlaying = autoplay
  selectedTrackId.value = track.id
  writeString('haoblog-music-track', track.id)
  resetMediaSource()
  if (wasPlaying) void playTrack(track)
}

function resetMediaSource() {
  const element = audio.value
  fallbackApplied.value = false
  useCors.value = spectrumEnabled.value && !dataSaver.value && !reduced.value
  currentTime.value = 0
  duration.value = 0
  if (!element) return
  element.pause()
  element.removeAttribute('src')
  delete element.dataset.trackId
  element.load()
}

async function playTrack(track = currentTrack.value) {
  const element = audio.value
  if (!element || !track) return
  errorMessage.value = ''
  selectedTrackId.value = track.id
  writeString('haoblog-music-track', track.id)
  if (element.dataset.trackId !== track.id) {
    element.dataset.trackId = track.id
    element.src = track.audioUrl
    element.load()
  }
  element.volume = volume.value
  element.muted = muted.value
  try {
    await element.play()
    await ensureAudioGraph()
    syncSpectrumLoop()
  } catch {
    playing.value = false
    errorMessage.value = '浏览器未允许播放或音频地址不可用。'
  }
}

function pauseTrack() { audio.value?.pause(); stopSpectrumLoop() }
function togglePlay() { if (playing.value) pauseTrack(); else void playTrack() }
function toggleMute() { muted.value = !muted.value; if (audio.value) audio.value.muted = muted.value }

function seek(value: number) {
  if (!audio.value || !Number.isFinite(duration.value)) return
  audio.value.currentTime = Math.min(duration.value, Math.max(0, value))
  currentTime.value = audio.value.currentTime
}

function handleEnded() {
  if (playMode.value === 'one') {
    if (audio.value) audio.value.currentTime = 0
    void playTrack()
  } else moveTrack(1, true)
}

function handleAudioError() {
  playing.value = false
  stopSpectrumLoop()
  if (useCors.value && !fallbackApplied.value) {
    fallbackApplied.value = true
    useCors.value = false
    spectrumEnabled.value = false
    spectrumMessage.value = '音频源未允许频谱跨域访问，已切换到普通播放。'
    releaseAudioGraph()
    const element = audio.value
    if (element) {
      element.removeAttribute('src')
      delete element.dataset.trackId
      element.load()
    }
    errorMessage.value = '频谱不可用；请再次按播放使用普通音频。'
    return
  }
  errorMessage.value = '音频加载失败，请检查地址与授权状态。'
}

async function toggleSpectrum() {
  if (dataSaver.value || reduced.value) return
  spectrumEnabled.value = !spectrumEnabled.value
  if (!spectrumEnabled.value) { stopSpectrumLoop(); return }
  useCors.value = true
  fallbackApplied.value = false
  if (playing.value) {
    resetMediaSource()
    await nextTick()
    await playTrack()
  }
}

async function ensureAudioGraph() {
  if (!audio.value || context || !useCors.value || !spectrumEnabled.value || dataSaver.value || reduced.value) return
  if (!('AudioContext' in window)) {
    spectrumEnabled.value = false
    spectrumMessage.value = '当前浏览器不支持 Web Audio，已保留普通播放。'
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
    spectrumMessage.value = '频谱已接入。'
  } catch {
    releaseAudioGraph()
    spectrumEnabled.value = false
    spectrumMessage.value = '频谱接入失败，已保留普通播放。'
  }
}

function syncSpectrumLoop() {
  const shouldRun = playing.value && hasSpectrum.value
  if (shouldRun && !raf) raf = requestAnimationFrame(drawSpectrum)
  if (!shouldRun) stopSpectrumLoop()
}

function stopSpectrumLoop() { if (raf) cancelAnimationFrame(raf); raf = 0 }

function drawSpectrum() {
  raf = 0
  if (!analyser.value || !canvas.value || !playing.value || !hasSpectrum.value) return
  const element = canvas.value
  const graphics = element.getContext('2d')
  if (!graphics) return
  const ratio = Math.min(window.devicePixelRatio || 1, 1.5)
  const width = Math.max(1, Math.floor(element.clientWidth * ratio))
  const height = Math.max(1, Math.floor(element.clientHeight * ratio))
  if (element.width !== width || element.height !== height) { element.width = width; element.height = height }
  const values = new Uint8Array(analyser.value.frequencyBinCount)
  analyser.value.getByteFrequencyData(values)
  graphics.clearRect(0, 0, width, height)
  graphics.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--color-accent').trim() || '#a8ff60'
  const gap = Math.max(1, width / values.length * .18)
  const barWidth = Math.max(1, width / values.length - gap)
  values.forEach((value, index) => {
    const barHeight = Math.max(1, value / 255 * height)
    graphics.fillRect(index * (barWidth + gap), height - barHeight, barWidth, barHeight)
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

function formatTime(seconds: number) {
  if (!Number.isFinite(seconds)) return '0:00'
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`
}
</script>

<template>
  <section class="music-strip" aria-label="音乐快捷控制">
    <button ref="openButton" class="track-summary" type="button" @click="emit('open')">
      <span>MUSIC / {{ playing ? 'PLAYING' : 'READY' }}</span><strong>{{ currentTrack?.title || '音乐控制台' }}</strong>
    </button>
    <button type="button" :aria-label="playing ? '暂停播放' : '开始播放'" :disabled="!currentTrack" @click="togglePlay">{{ playing ? 'PAUSE' : 'PLAY' }}</button>
  </section>

  <dialog ref="dialog" class="music-console" aria-labelledby="music-console-title" @close="handleDialogClose">
    <div class="console-head">
      <div><p class="instrument-label">GLOBAL / MUSIC CONSOLE</p><h2 id="music-console-title">音乐控制台</h2></div>
      <button type="button" aria-label="关闭音乐控制台" @click="dialog?.close()">CLOSE</button>
    </div>
    <p v-if="loading" class="console-note" role="status">正在读取音乐清单…</p>
    <p v-else-if="!currentTrack" class="console-note console-warn" role="alert">{{ errorMessage }}</p>
    <template v-else>
      <div class="console-layout">
        <ol class="track-list" aria-label="曲目列表">
          <li v-for="(track, index) in tracks" :key="track.id">
            <button type="button" :aria-current="track.id === currentTrack.id ? 'true' : undefined" @click="selectTrack(track)">
              <span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ track.title }}</strong><small>{{ track.artist }}</small>
            </button>
          </li>
        </ol>
        <div class="player-panel">
          <div class="now-playing"><div><p>NOW PLAYING</p><h3>{{ currentTrack.title }}</h3><span>{{ currentTrack.artist }}</span></div><span>{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span></div>
          <canvas v-show="hasSpectrum" ref="canvas" class="spectrum" role="img" aria-label="当前音频频谱" />
          <p class="console-note" role="status">{{ spectrumMessage }}</p>
          <label class="seek-line">播放进度<input :value="currentTime" type="range" min="0" :max="duration || 0" step=".1" aria-label="播放进度" @input="seek(Number(($event.target as HTMLInputElement).value))"></label>
          <div class="transport">
            <button type="button" aria-label="上一首" @click="moveTrack(-1)">PREV</button>
            <button type="button" class="primary" :aria-label="playing ? '暂停播放' : '开始播放'" @click="togglePlay">{{ playing ? 'PAUSE' : 'PLAY' }}</button>
            <button type="button" aria-label="下一首" @click="moveTrack(1)">NEXT</button>
          </div>
          <div class="settings-row">
            <button type="button" @click="playMode = playMode === 'all' ? 'one' : 'all'">{{ playMode === 'all' ? '顺序循环' : '单曲循环' }}</button>
            <button type="button" @click="toggleMute">{{ muted ? '取消静音' : '静音' }}</button>
            <label>音量<input v-model.number="volume" type="range" min="0" max="1" step=".01" aria-label="音量"></label>
            <button type="button" :aria-pressed="spectrumEnabled" :disabled="Boolean(dataSaver || reduced)" @click="toggleSpectrum">{{ spectrumEnabled ? '频谱开启' : '频谱关闭' }}</button>
          </div>
          <p v-if="errorMessage" class="console-note console-warn" role="alert">{{ errorMessage }}</p>
          <p class="license">授权：{{ currentTrack.licenseName }} <a v-if="currentTrack.licenseUrl" :href="currentTrack.licenseUrl" target="_blank" rel="noopener noreferrer">授权链接 ↗</a> <a v-if="currentTrack.sourceUrl" :href="currentTrack.sourceUrl" target="_blank" rel="noopener noreferrer">来源 ↗</a></p>
        </div>
      </div>
    </template>
    <audio ref="audio" :crossorigin="useCors ? 'anonymous' : undefined" preload="none" @play="playing = true" @pause="playing = false" @ended="handleEnded" @error="handleAudioError" @loadedmetadata="duration = audio?.duration || 0" @durationchange="duration = audio?.duration || 0" @timeupdate="currentTime = audio?.currentTime || 0" />
  </dialog>
</template>

<style scoped>
.music-strip { position: fixed; z-index: var(--z-dock); right: var(--space-shell); bottom: 60px; display: flex; min-width: min(24rem, calc(100vw - 1.5rem)); border: 1px solid var(--color-border); background: var(--color-bg-sub); box-shadow: 0 -8px 24px rgb(0 0 0 / .18); }.music-strip button { border: 0; border-left: 1px solid var(--color-border); border-radius: 0; background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1.2 var(--font-mono); }.track-summary { display: grid; flex: 1; gap: .15rem; padding: .45rem .65rem; text-align: left; }.track-summary span { color: var(--color-text-muted); }.track-summary strong { overflow: hidden; max-width: 24ch; text-overflow: ellipsis; white-space: nowrap; }.music-strip > button:last-child { width: 4.5rem; }
.music-console { width: min(70rem, calc(100vw - 2rem)); max-height: calc(100vh - 5rem); margin: auto auto 3.5rem; padding: 1rem; overflow: auto; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg); color: var(--color-text-main); }.music-console::backdrop { background: rgb(1 9 8 / .78); backdrop-filter: blur(4px); }.console-head { display: flex; align-items: start; justify-content: space-between; gap: 1rem; padding-bottom: .8rem; border-bottom: 1px solid var(--color-border); }.console-head h2 { margin: .35rem 0 0; font: 800 clamp(2rem, 7vw, 4rem)/.9 var(--font-display); }.music-console button { min-height: 2.5rem; padding: .5rem .7rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); cursor: pointer; font: var(--text-xs)/1.2 var(--font-mono); }.music-console button:hover, .music-console button:focus-visible, .music-console button[aria-current='true'], .music-console button[aria-pressed='true'], .music-console .primary { border-color: var(--color-accent); color: var(--color-accent); }
.console-layout { display: grid; grid-template-columns: minmax(13rem, .72fr) minmax(0, 1.7fr); gap: 1rem; margin-top: 1rem; }.track-list { max-height: 29rem; margin: 0; padding: 0; overflow: auto; list-style: none; }.track-list button { display: grid; grid-template-columns: auto 1fr; width: 100%; gap: .2rem .65rem; text-align: left; }.track-list li + li button { border-top: 0; }.track-list span, .track-list small { color: var(--color-text-muted); }.track-list small { grid-column: 2; }.player-panel { min-width: 0; }.now-playing { display: flex; align-items: end; justify-content: space-between; gap: 1rem; }.now-playing p, .now-playing > span, .console-note, .license, .seek-line, .settings-row label { color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }.now-playing h3 { margin: .3rem 0; font: 800 clamp(1.6rem, 5vw, 3rem)/1 var(--font-display); }.spectrum { display: block; width: 100%; height: 7rem; margin-top: 1rem; border-bottom: 1px solid var(--color-border); }.seek-line { display: grid; gap: .35rem; margin: .8rem 0; }.seek-line input, .settings-row input { accent-color: var(--color-accent); }.transport, .settings-row { display: flex; flex-wrap: wrap; gap: .45rem; }.transport .primary { min-width: 7rem; }.settings-row { margin-top: .65rem; }.settings-row label { display: flex; align-items: center; gap: .4rem; padding-inline: .4rem; }.console-warn { color: var(--color-warn); }.license a { margin-left: .5rem; color: var(--color-accent); }
@media (max-width: 700px) { .music-strip { right: .75rem; }.music-console { width: calc(100vw - 1rem); margin-bottom: 3.25rem; padding: .75rem; }.console-layout { grid-template-columns: 1fr; }.track-list { display: flex; max-height: none; overflow-x: auto; }.track-list li { min-width: 13rem; }.track-list li + li button { border-top: 1px solid var(--color-border); border-left: 0; }.now-playing { align-items: start; flex-direction: column; }.spectrum { height: 4.5rem; } }
@media (prefers-reduced-motion: reduce) { .spectrum { display: none; } }
</style>
