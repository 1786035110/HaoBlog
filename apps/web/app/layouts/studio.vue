<template>
  <a class="studio-skip" href="#studio-main">跳到工作台主要内容</a>
  <div class="studio-shell">
    <div class="phosphor-hairline" aria-hidden="true" />
    <header class="studio-header">
      <div>
        <p class="instrument-label">HAOBLOG / STUDIO</p>
        <p class="studio-caption">夜间写作仪 / {{ session?.username || 'REQUEST ACCESS' }}</p>
      </div>
      <nav v-if="session" class="studio-nav" aria-label="工作台导航">
        <NuxtLink to="/studio/articles" :aria-current="route.path === '/studio/articles' ? 'page' : undefined">文章日志</NuxtLink>
        <NuxtLink to="/studio/articles/new">新建文章</NuxtLink>
        <NuxtLink to="/studio/comments" :aria-current="route.path === '/studio/comments' ? 'page' : undefined">评论审核</NuxtLink>
        <NuxtLink to="/studio/settings" :aria-current="route.path === '/studio/settings' ? 'page' : undefined">站点开关</NuxtLink>
        <NuxtLink to="/" external>返回公开站</NuxtLink>
        <button type="button" :disabled="pending" @click="signOut">{{ pending ? 'CLEARING…' : '退出' }}</button>
      </nav>
    </header>
    <main id="studio-main" class="studio-stage">
      <p v-if="!initialized" class="studio-loading" role="status">正在校准 Session 信号…</p>
      <slot v-else-if="route.path === '/studio' || session" />
    </main>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const { session, pending, initialized, restore, logout } = useAdminSession()
useHead({ meta: [{ name: 'robots', content: 'noindex,nofollow' }] })

onMounted(async () => {
  const active = await restore()
  if (active && route.path === '/studio') await navigateTo('/studio/articles')
  if (!active && route.path !== '/studio') await navigateTo('/studio')
})

watch(session, async (value) => {
  if (!value && initialized.value && route.path !== '/studio') await navigateTo('/studio')
})

async function signOut() {
  if (await logout()) await navigateTo('/studio')
}
</script>

<style scoped>
.studio-shell { min-height: 100vh; background: var(--color-bg-base); color: var(--color-text-main); }
.studio-header { display: flex; align-items: end; justify-content: space-between; gap: var(--space-6); padding: var(--space-6) var(--space-shell) var(--space-4); border-bottom: 1px solid var(--color-border); }
.studio-caption { margin: .4rem 0 0; color: var(--color-text-muted); font: var(--text-sm)/1.4 var(--font-mono); }
.studio-nav { display: flex; flex-wrap: wrap; align-items: center; justify-content: end; gap: var(--space-2) var(--space-4); }
.studio-nav a, .studio-nav button { padding: .4rem 0; border: 0; border-bottom: 1px solid transparent; background: transparent; color: var(--color-text-muted); font: var(--text-xs)/1.2 var(--font-mono); text-decoration: none; cursor: pointer; }
.studio-nav a:hover, .studio-nav a:focus-visible, .studio-nav a[aria-current='page'], .studio-nav button:hover:not(:disabled), .studio-nav button:focus-visible { border-bottom-color: var(--color-accent); color: var(--color-accent); }
.studio-nav button:disabled { cursor: wait; opacity: .55; }
.studio-stage { min-height: calc(100vh - 7.25rem); padding: var(--space-8) var(--space-shell) 6rem; }
.studio-loading { max-width: 52rem; margin: 10vh auto; color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.studio-skip { position: fixed; z-index: var(--z-skip); top: var(--space-2); left: var(--space-2); transform: translateY(-160%); padding: var(--space-2) var(--space-3); background: var(--color-accent); color: var(--color-bg-base); font: 600 var(--text-sm)/1 var(--font-mono); text-decoration: none; }
.studio-skip:focus { transform: translateY(0); }
@media (max-width: 640px) { .studio-header { align-items: start; flex-direction: column; } .studio-nav { justify-content: start; } }
@media (max-width: 360px) { .studio-header { padding-inline: .75rem; } .studio-stage { padding-inline: .75rem; } }
</style>
