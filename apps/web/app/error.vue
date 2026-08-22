<template>
  <section class="observation-scene error-scene" aria-labelledby="error-title">
    <p class="instrument-label">SIGNAL LOST / {{ error?.statusCode ?? 404 }}</p>
    <h1 id="error-title">页面未找到</h1>
    <p class="signal-copy">这条路径没有被观测站记录。</p>
    <NuxtLink to="/" class="dock-control">返回 INDEX</NuxtLink>
  </section>
</template>

<script setup lang="ts">
defineProps<{ error: { statusCode?: number; statusMessage?: string } }>()
useHead({ meta: [{ name: 'robots', content: 'noindex,nofollow' }] })
</script>
