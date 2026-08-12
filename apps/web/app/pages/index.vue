<script setup lang="ts">
const config = useRuntimeConfig()
const { data: site } = await useFetch<{ title: string; description: string }>('/api/v1/public/site', {
  baseURL: config.public.apiBase,
  default: () => ({ title: 'HaoBlog', description: '极夜观测站' }),
})
</script>

<template>
  <section class="observation-scene" aria-labelledby="site-title">
    <p class="instrument-label">OBSERVATION / INITIAL FRAME</p>
    <h1 id="site-title">{{ site?.title }}</h1>
    <p class="signal-copy">{{ site?.description }} · 一个在夜间校准信号的开发者空间。</p>
    <p class="signal-note">公共内容优先 SSR；复杂仪器将在后续阶段按需接入。</p>
  </section>
</template>
