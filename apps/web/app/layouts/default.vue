<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="site-shell">
    <div class="phosphor-hairline" aria-hidden="true" />
    <main id="main-content" class="content-stage"><slot /></main>
    <ReadingProgress />
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
        <button class="dock-control dock-control--disabled" type="button" disabled aria-disabled="true" aria-describedby="command-help">⌘K</button>
        <span id="command-help" class="dock-help">命令入口即将开放</span>
      </div>
      <div class="dock-status">
        <button class="dock-control dock-control--disabled" type="button" disabled aria-disabled="true" aria-describedby="ai-help">AI</button>
        <span id="ai-help" class="dock-help">AI 入口即将开放</span>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const routeItems = [
  { to: '/', label: '01 / 首页' },
  { to: '/articles', label: '02 / 文章' },
  { to: '/garden', label: '03 / 花园' },
  { to: '/tools', label: '04 / 工具' },
  { to: '/about', label: '05 / 关于' },
]
</script>
