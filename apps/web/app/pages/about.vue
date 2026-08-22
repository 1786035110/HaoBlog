<script setup lang="ts">
import { buildPublicPageSeo, publicPageHead } from '~/utils/publicArticleSeo'
import { defaultPublicSite, usePublicSite } from '~/utils/publicSite'

const { data: site } = await usePublicSite()
const resolvedSite = site.value || defaultPublicSite
useHead(() => publicPageHead(buildPublicPageSeo(resolvedSite, '/about', `关于观测站 · ${resolvedSite.title}`, '这里记录 Java、Vue、架构与数字花园的长期实验。')))
</script>

<template><section class="observation-scene" aria-labelledby="about-title"><p class="instrument-label">ABOUT / OPERATOR</p><h1 id="about-title">关于观测站</h1><p class="signal-copy">这里记录 Java、Vue、架构与数字花园的长期实验。</p></section></template>
