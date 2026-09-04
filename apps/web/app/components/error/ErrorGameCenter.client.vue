<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'

type GameId = 'snake' | '2048' | 'mines'
const selected = ref<GameId | null>(null)
const games = [
  { id: 'snake' as const, number: '01', name: '贪吃蛇', description: '方向键或触屏方向键' },
  { id: '2048' as const, number: '02', name: '2048', description: '合并数字直到无路可走' },
  { id: 'mines' as const, number: '03', name: '扫雷', description: '9 × 9 / 10 雷' },
]
const components = {
  snake: defineAsyncComponent(() => import('./SnakeGame.client.vue')),
  '2048': defineAsyncComponent(() => import('./Game2048.client.vue')),
  mines: defineAsyncComponent(() => import('./MinesweeperGame.client.vue')),
}
</script>

<template>
  <section class="game-center" aria-labelledby="game-center-title">
    <header>
      <p class="instrument-label">404 / GAME BREAK</p>
      <h2 id="game-center-title">选一个小游戏</h2>
      <p>不存成绩，不设排行，随时可以离开。</p>
    </header>
    <div class="game-picker" role="group" aria-label="小游戏选择">
      <button v-for="game in games" :key="game.id" type="button" :aria-pressed="selected === game.id" @click="selected = game.id">
        <span>{{ game.number }}</span><strong>{{ game.name }}</strong><small>{{ game.description }}</small>
      </button>
    </div>
    <Suspense v-if="selected">
      <component :is="components[selected]" />
      <template #fallback><p class="game-loading" role="status">正在准备游戏…</p></template>
    </Suspense>
  </section>
</template>

<style scoped>
.game-center { margin-top: 1.5rem; padding: 1rem 0 1.25rem; border-block: 1px solid var(--color-border); }
.game-center header { display: grid; gap: .45rem; }
.game-center h2 { margin: 0; font: 700 clamp(1.6rem, 5vw, 2.8rem)/1 var(--font-display); }
.game-center header > p:last-child, .game-loading { margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
.game-picker { display: grid; grid-template-columns: repeat(3, 1fr); gap: .5rem; margin: 1rem 0; }
.game-picker button { display: grid; grid-template-columns: auto 1fr; gap: .2rem .65rem; padding: .75rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); text-align: left; cursor: pointer; }
.game-picker button:hover, .game-picker button:focus-visible, .game-picker button[aria-pressed='true'] { border-color: var(--color-accent); }
.game-picker span, .game-picker small { color: var(--color-text-muted); font: var(--text-xs)/1.3 var(--font-mono); }
.game-picker strong { font: 700 var(--text-sm)/1.2 var(--font-body); }
.game-picker small { grid-column: 2; }
@media (max-width: 640px) { .game-picker { grid-template-columns: 1fr; } }
</style>
