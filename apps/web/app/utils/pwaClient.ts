export type PwaPreparationResult = { cached: number }

function saveDataEnabled() {
  return Boolean((navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData)
}

export async function registerServiceWorker(options: { ignoreSaveData?: boolean } = {}) {
  if (!('serviceWorker' in navigator) || (!options.ignoreSaveData && saveDataEnabled())) return null
  try {
    return await navigator.serviceWorker.register('/sw.js', { scope: '/' })
  } catch {
    return null
  }
}

export function sameOriginResourceUrls() {
  const origin = window.location.origin
  const urls = new Set<string>()
  for (const entry of performance.getEntriesByType('resource')) {
    const resource = entry as PerformanceResourceTiming
    try {
      const url = new URL(resource.name)
      if (url.origin === origin && (url.pathname.startsWith('/_nuxt/') || url.pathname.startsWith('/workers/'))) urls.add(url.toString())
    } catch { /* 忽略浏览器无法解析的资源记录。 */ }
  }
  return [...urls]
}

function activeWorker(registration: ServiceWorkerRegistration) {
  return registration.active || registration.waiting || registration.installing
}

export async function prepareOfflineTools(resourceUrls: string[]): Promise<PwaPreparationResult> {
  const registration = await registerServiceWorker({ ignoreSaveData: true })
  const worker = registration && activeWorker(registration)
  if (!registration || !worker) throw new Error('Service Worker 暂时不可用，工具仍可在线使用。')
  return await new Promise((resolve, reject) => {
    const channel = new MessageChannel()
    const timer = window.setTimeout(() => reject(new Error('离线工具准备超时，请保持网络连接后重试。')), 15000)
    channel.port1.onmessage = event => {
      window.clearTimeout(timer)
      const data = event.data as { ok?: boolean; cached?: number; error?: string }
      if (data.ok) resolve({ cached: data.cached || 0 })
      else reject(new Error(data.error || '离线工具准备失败，请稍后重试。'))
    }
    worker.postMessage({ type: 'PREPARE_TOOLS', resourceUrls }, [channel.port2])
  })
}
