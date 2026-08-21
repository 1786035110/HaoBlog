import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

const pageProgress = ref(0)
const articleProgress = ref(0)
let subscriberCount = 0
let frame = 0
let articleElement: HTMLElement | null = null

const clamp = (value: number) => Math.min(1, Math.max(0, value))

function updateProgress() {
  frame = 0
  const maxScroll = document.documentElement.scrollHeight - window.innerHeight
  pageProgress.value = maxScroll > 0 ? clamp(window.scrollY / maxScroll) : 0

  if (!articleElement) {
    articleProgress.value = pageProgress.value
    return
  }

  const rect = articleElement.getBoundingClientRect()
  const articleTop = window.scrollY + rect.top
  const articleHeight = Math.max(articleElement.scrollHeight, rect.height)
  const start = articleTop - window.innerHeight * 0.15
  const end = articleTop + articleHeight - window.innerHeight * 0.85
  articleProgress.value = end > start ? clamp((window.scrollY - start) / (end - start)) : 0
}

function requestProgressUpdate() {
  if (frame) return
  frame = requestAnimationFrame(updateProgress)
}

function startProgressListener() {
  window.addEventListener('scroll', requestProgressUpdate, { passive: true })
  window.addEventListener('resize', requestProgressUpdate, { passive: true })
  requestProgressUpdate()
}

function stopProgressListener() {
  window.removeEventListener('scroll', requestProgressUpdate)
  window.removeEventListener('resize', requestProgressUpdate)
  if (frame) cancelAnimationFrame(frame)
  frame = 0
}

export function useScrollProgress(target?: Ref<HTMLElement | null>) {
  const stopTargetWatch = target
    ? watch(target, value => { articleElement = value }, { flush: 'post' })
    : undefined

  onMounted(() => {
    subscriberCount += 1
    if (target?.value) articleElement = target.value
    if (subscriberCount === 1) startProgressListener()
    else requestProgressUpdate()
  })

  onBeforeUnmount(() => {
    stopTargetWatch?.()
    if (target && articleElement === target.value) articleElement = null
    subscriberCount -= 1
    if (subscriberCount === 0) stopProgressListener()
  })

  return { progress: pageProgress, articleProgress }
}
