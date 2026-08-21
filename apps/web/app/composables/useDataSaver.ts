import { onBeforeUnmount, onMounted, readonly, ref } from 'vue'

export function useDataSaver() {
  const enabled = ref(false)
  let connection: (EventTarget & { saveData?: boolean }) | undefined
  const update = () => { enabled.value = connection?.saveData === true }
  onMounted(() => {
    connection = (navigator as Navigator & { connection?: EventTarget & { saveData?: boolean } }).connection
    update()
    connection?.addEventListener('change', update)
  })
  onBeforeUnmount(() => connection?.removeEventListener('change', update))
  return { enabled: readonly(enabled) }
}
