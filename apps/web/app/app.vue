<template>
  <NuxtLayout>
    <NuxtPage />
    <aside v-if="chunkRecovery" class="chunk-recovery" role="alert">
      <span>页面文件已更新，当前内容仍保留。请手动刷新以恢复。</span>
      <button type="button" @click="reloadPage">刷新页面</button>
    </aside>
  </NuxtLayout>
</template>

<script setup lang="ts">
import { publicAbsoluteUrl } from '~/utils/publicArticleSeo'
import { usePublicSite } from '~/utils/publicSite'
import { useDataSaver } from '~/composables/useDataSaver'

const { data: site } = await usePublicSite()
const { enabled: dataSaver } = useDataSaver()
const runtimeConfig = useRuntimeConfig()
const chunkRecovery = ref(false)
function onPreloadError(event: Event) {
  event.preventDefault()
  chunkRecovery.value = true
}
function reloadPage() { window.location.reload() }
onMounted(() => window.addEventListener('vite:preloadError', onPreloadError))
onBeforeUnmount(() => window.removeEventListener('vite:preloadError', onPreloadError))
useHead(() => ({
  htmlAttrs: { lang: 'zh-CN', 'data-save-data': dataSaver.value ? 'on' : 'off' },
  link: [
    {
      rel: 'alternate',
      type: 'application/rss+xml',
      title: `${site.value?.title || 'HaoBlog'} RSS`,
      href: site.value ? publicAbsoluteUrl(site.value.siteUrl, '/rss.xml') || undefined : undefined,
    },
    ...(runtimeConfig.public.pwaEnabled ? [{ rel: 'manifest' as const, href: '/manifest.webmanifest' }] : []),
  ],
}))
</script>

<style scoped>
.chunk-recovery { position: fixed; z-index: 80; right: 1rem; bottom: 4rem; display: flex; align-items: center; gap: .75rem; max-width: min(32rem, calc(100vw - 2rem)); padding: .75rem; border: 1px solid var(--color-warn); background: var(--color-bg-base); color: var(--color-text-main); font: var(--text-xs)/1.5 var(--font-mono); }
.chunk-recovery button { min-height: 44px; border: 1px solid var(--color-accent); border-radius: 0; background: transparent; color: var(--color-accent); cursor: pointer; }
@media (max-width: 360px) { .chunk-recovery { left: .5rem; right: .5rem; flex-direction: column; align-items: stretch; }.chunk-recovery button { width: 100%; } }
</style>
