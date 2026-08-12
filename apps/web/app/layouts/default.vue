<template>
  <div class="site-shell">
    <div class="phosphor-hairline" aria-hidden="true" />
    <main class="content-stage">
      <slot />
    </main>
    <div class="dock-progress" aria-hidden="true" />
    <nav class="instrument-dock" aria-label="站点仪表条">
      <NuxtLink to="/" class="dock-control">INDEX</NuxtLink>
      <button class="dock-control" type="button" aria-label="信号终端（尚未启用）">⌘K</button>
      <button class="dock-control" type="button" aria-label="AI 助手（尚未启用）">AI</button>
    </nav>
  </div>
</template>
