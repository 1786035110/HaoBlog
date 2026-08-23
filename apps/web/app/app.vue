<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

<script setup lang="ts">
import { publicAbsoluteUrl } from '~/utils/publicArticleSeo'
import { usePublicSite } from '~/utils/publicSite'
import { useDataSaver } from '~/composables/useDataSaver'

const { data: site } = await usePublicSite()
const { enabled: dataSaver } = useDataSaver()
const runtimeConfig = useRuntimeConfig()
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
