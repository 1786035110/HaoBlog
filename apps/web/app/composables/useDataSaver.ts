import { onBeforeUnmount, onMounted, readonly, ref } from 'vue'

export function useDataSaver() {
  const requestEvent = import.meta.server && typeof useRequestEvent === 'function' ? useRequestEvent() : undefined
  const initialSaveData = import.meta.server
    && requestEvent
    && typeof requestEvent.node.req.headers['save-data'] === 'string'
    && requestEvent.node.req.headers['save-data'].toLowerCase() === 'on'
  const enabled = typeof useState === 'function'
    ? useState('haoblog-save-data', () => initialSaveData)
    : ref(false)
  const serverEnabled = enabled.value
  let connection: (EventTarget & { saveData?: boolean }) | undefined
  const update = () => {
    if (typeof connection?.saveData === 'boolean') enabled.value = serverEnabled || connection.saveData
  }
  onMounted(() => {
    connection = (navigator as Navigator & { connection?: EventTarget & { saveData?: boolean } }).connection
    update()
    connection?.addEventListener('change', update)
  })
  onBeforeUnmount(() => connection?.removeEventListener('change', update))
  return { enabled: readonly(enabled) }
}
