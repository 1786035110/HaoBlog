<script setup lang="ts">
import type { Component } from 'vue'

type ErrorInfo = { statusCode?: number; statusMessage?: string }
const props = defineProps<{ error: ErrorInfo }>()
const runtimeConfig = useRuntimeConfig()
const gameComponent = ref<Component | null>(null)
const gamesAvailable = ref(false)
const gamesLoading = ref(false)
const isNotFound = computed(() => (props.error?.statusCode ?? 404) === 404)
const title = computed(() => isNotFound.value ? '页面未找到' : '观测站暂时失联')
const copy = computed(() => isNotFound.value ? '这条路径没有被观测站记录。' : '当前请求没有得到可用的观测响应，请稍后重试。')

useHead({
  title: () => `${title.value} · HaoBlog`,
  meta: [{ name: 'robots', content: 'noindex,nofollow' }],
})

onMounted(async () => {
  gamesAvailable.value = isNotFound.value && Boolean(runtimeConfig.public.signalRepair404)
})

async function openGames() {
  if (gameComponent.value || gamesLoading.value) return
  gamesLoading.value = true
  try {
    gameComponent.value = (await import('./components/error/ErrorGameCenter.client.vue')).default
  } finally {
    gamesLoading.value = false
  }
}

function retry() {
  if (import.meta.client) window.location.reload()
}
</script>

<template>
  <section class="observation-scene error-scene" aria-labelledby="error-title">
    <p class="instrument-label">SIGNAL LOST / {{ props.error?.statusCode ?? 404 }}</p>
    <h1 id="error-title">{{ title }}</h1>
    <p class="signal-copy">{{ copy }}</p>

    <button v-if="gamesAvailable && !gameComponent" class="game-launcher" type="button" :disabled="gamesLoading" @click="openGames">
      {{ gamesLoading ? 'LOADING…' : '玩个小游戏' }}
    </button>
    <component :is="gameComponent" v-if="gameComponent" />

    <div class="error-recovery" aria-label="错误恢复路径">
      <form v-if="isNotFound" class="error-search" action="/search" method="get">
        <label for="error-search-query">搜索文章</label>
        <div><input id="error-search-query" name="q" type="search" minlength="2" maxlength="100" required placeholder="输入至少 2 个字符"><button type="submit">SEARCH →</button></div>
      </form>
      <button v-else class="retry-button" type="button" @click="retry">重试当前路径</button>
      <nav class="error-links" aria-label="错误页入口">
        <NuxtLink to="/articles">文章观测日志</NuxtLink>
        <NuxtLink to="/tools">公开工具箱</NuxtLink>
        <NuxtLink to="/">返回首页</NuxtLink>
      </nav>
    </div>
  </section>
</template>

<style scoped>
.error-scene { max-width: 60rem; }
.error-recovery { margin-top: 2rem; padding-top: 1.2rem; border-top: 1px solid var(--color-border); }
.error-search { display: grid; gap: .45rem; max-width: 34rem; color: var(--color-text-muted); font: var(--text-xs)/1.3 var(--font-mono); }
.error-search > div { display: flex; gap: .5rem; }
.error-search input { flex: 1; min-width: 0; min-height: 2.5rem; padding: .5rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); font: var(--text-sm)/1.2 var(--font-mono); }
.error-search button, .retry-button, .game-launcher { min-height: 2.5rem; padding: .5rem .7rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1.2 var(--font-mono); }
.game-launcher { margin-top: 1.5rem; }
.error-search button:hover, .error-search button:focus-visible, .retry-button:hover, .retry-button:focus-visible, .game-launcher:hover, .game-launcher:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
.error-links { display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 1.2rem; }
.error-links a { color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); }
@media (max-width: 360px) { .error-search > div { align-items: stretch; flex-direction: column; }.error-search button { width: 100%; } }
</style>
