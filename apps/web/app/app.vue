<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

<script setup lang="ts">
import { publicAbsoluteUrl } from '~/utils/publicArticleSeo'
import { usePublicSite } from '~/utils/publicSite'

const { data: site } = await usePublicSite()
useHead(() => ({
  link: [{
    rel: 'alternate',
    type: 'application/rss+xml',
    title: `${site.value?.title || 'HaoBlog'} RSS`,
    href: site.value ? publicAbsoluteUrl(site.value.siteUrl, '/rss.xml') || undefined : undefined,
  }],
}))
</script>
