<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="site-shell">
    <div class="phosphor-hairline" aria-hidden="true" />
    <main id="main-content" class="content-stage"><slot /></main>
    <ReadingProgress />
    <component :is="musicComponent" v-if="musicComponent" :open="musicOpen" :manifest-url="site?.musicManifestUrl || ''" @open="musicOpen = true" @close="musicOpen = false" />
    <button v-else-if="site?.musicEnabled && site.musicManifestUrl" class="music-launcher" type="button" @click="openMusic">MUSIC / OPEN CONSOLE</button>
    <nav class="instrument-dock" aria-label="站点仪表板">
      <details class="dock-index">
        <summary class="dock-control dock-index-trigger" aria-label="INDEX：打开站点索引">
          <span>INDEX</span><span class="dock-glyph" aria-hidden="true">+</span>
        </summary>
        <div class="route-menu" aria-label="真实路由菜单">
          <NuxtLink v-for="item in routeItems" :key="item.to" :to="item.to" :prefetch="false" :aria-current="route.path === item.to ? 'page' : undefined">{{ item.label }}</NuxtLink>
        </div>
      </details>
      <div class="dock-status">
        <button ref="terminalTrigger" class="dock-control" type="button" aria-keyshortcuts="Control+K Meta+K" aria-describedby="command-help" @click="openTerminal">⌘K</button>
        <span id="command-help" class="dock-help">打开安全终端</span>
      </div>
      <div class="dock-status">
        <button class="dock-control dock-control--disabled" type="button" disabled aria-disabled="true" aria-describedby="ai-help">AI</button>
        <span id="ai-help" class="dock-help">AI 入口即将开放</span>
      </div>
    </nav>
    <component :is="terminalComponent" v-if="terminalComponent" :open="terminalOpen" @close="closeTerminal" />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { usePublicSite } from '../utils/publicSite'

const route = useRoute()
const { data: site } = usePublicSite()
const terminalTrigger = ref<HTMLButtonElement | null>(null)
const terminalComponent = ref<Component | null>(null)
const musicComponent = ref<Component | null>(null)
const musicOpen = ref(false)
const terminalOpen = ref(false)
let terminalLoad: Promise<void> | null = null
let musicLoad: Promise<void> | null = null
const routeItems = [
  { to: '/', label: '01 / 首页' },
  { to: '/articles', label: '02 / 文章' },
  { to: '/garden', label: '03 / 花园' },
  { to: '/tools', label: '04 / 工具' },
  { to: '/about', label: '05 / 关于' },
]

async function openTerminal() {
  terminalOpen.value = true
  if (terminalComponent.value || terminalLoad) return terminalLoad
  terminalLoad = import('../components/terminal/CommandTerminal.client.vue').then(module => {
    terminalComponent.value = module.default
  }).finally(() => {
    terminalLoad = null
  })
  return terminalLoad
}

function closeTerminal() {
  terminalOpen.value = false
  nextTick(() => terminalTrigger.value?.focus())
}

async function openMusic() {
  musicOpen.value = true
  if (musicComponent.value || musicLoad) return musicLoad
  musicLoad = import('../components/music/MusicConsole.client.vue').then(module => {
    musicComponent.value = module.default
  }).finally(() => {
    musicLoad = null
  })
  return musicLoad
}

function handleGlobalShortcut(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    void openTerminal()
  }
}

onMounted(() => window.addEventListener('keydown', handleGlobalShortcut))
onBeforeUnmount(() => window.removeEventListener('keydown', handleGlobalShortcut))
</script>

<style scoped>
.music-launcher { position: fixed; z-index: var(--z-dock); right: var(--space-shell); bottom: 60px; padding: .6rem .75rem; border: 1px solid var(--color-border); background: var(--color-bg-sub); color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1.2 var(--font-mono); letter-spacing: .1em; }
.music-launcher:hover, .music-launcher:focus-visible { border-color: var(--color-accent); }
@media (max-width: 640px) { .music-launcher { right: .75rem; } }
</style>
