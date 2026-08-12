<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="site-shell">
    <div class="phosphor-hairline" aria-hidden="true" />
    <main id="main-content" class="content-stage"><slot /></main>
    <ReadingProgress />
    <nav class="instrument-dock" aria-label="站点仪表板">
      <details class="dock-index">
        <summary class="dock-control">INDEX</summary>
        <div class="route-menu" aria-label="真实路由菜单">
          <NuxtLink v-for="item in routeItems" :key="item.to" :to="item.to" :aria-current="route.path === item.to ? 'page' : undefined">{{ item.label }}</NuxtLink>
        </div>
      </details>
      <div class="dock-status">
        <button class="dock-control" type="button" disabled aria-disabled="true" aria-describedby="command-help">⌘K</button>
        <span id="command-help" class="dock-help">命令入口即将开放</span>
      </div>
      <div class="dock-status">
        <button class="dock-control" type="button" disabled aria-disabled="true" aria-describedby="ai-help">AI</button>
        <span id="ai-help" class="dock-help">AI 入口即将开放</span>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const routeItems = [
  { to: '/', label: '首页 / INDEX' },
  { to: '/articles', label: '文章 / ARTICLES' },
  { to: '/garden', label: '花园 / GARDEN' },
  { to: '/tools', label: '工具 / TOOLS' },
  { to: '/about', label: '关于 / ABOUT' },
  { to: '/studio', label: '工作台 / STUDIO' },
]
</script>
