import { registerServiceWorker } from '~/utils/pwaClient'

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  if (!config.public.pwaEnabled) return
  // Save-Data 下不自动注册；工具页的显式准备动作仍会主动注册。
  void registerServiceWorker()
})
