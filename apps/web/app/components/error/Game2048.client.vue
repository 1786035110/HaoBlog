<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { add2048Tile, move2048, shouldIgnoreGameKey, type MoveDirection } from '../../utils/errorGames'

const board = ref<number[]>([])
const status = ref('使用方向键、WASD 或屏幕方向键移动。')

function reset() {
  board.value = add2048Tile(add2048Tile(Array(16).fill(0)))
  status.value = '新游戏已开始。'
}

function move(direction: MoveDirection) {
  const result = move2048(board.value, direction)
  if (!result.moved) {
    status.value = '这个方向无法移动。'
    return
  }
  board.value = add2048Tile(result.board)
  const largest = Math.max(...board.value)
  status.value = largest >= 2048 ? `已合成 ${largest}，可以继续。` : `当前最大数字 ${largest}。`
}

function onKeydown(event: KeyboardEvent) {
  if (shouldIgnoreGameKey(event)) return
  const direction = ({ ArrowLeft: 'left', a: 'left', A: 'left', ArrowRight: 'right', d: 'right', D: 'right', ArrowUp: 'up', w: 'up', W: 'up', ArrowDown: 'down', s: 'down', S: 'down' } as Record<string, MoveDirection>)[event.key]
  if (!direction) return
  event.preventDefault()
  move(direction)
}

onMounted(() => { reset(); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="mini-game" aria-labelledby="game-2048-title">
    <div class="game-head"><h3 id="game-2048-title">2048</h3><button type="button" @click="reset">重新开始</button></div>
    <div class="board-2048" role="grid" aria-label="2048 棋盘">
      <span v-for="(value, index) in board" :key="index" role="gridcell" :data-value="value || undefined">{{ value || '' }}</span>
    </div>
    <div class="direction-pad" aria-label="移动方向">
      <button type="button" aria-label="向上" @click="move('up')">↑</button>
      <button type="button" aria-label="向左" @click="move('left')">←</button>
      <button type="button" aria-label="向下" @click="move('down')">↓</button>
      <button type="button" aria-label="向右" @click="move('right')">→</button>
    </div>
    <p role="status" aria-live="polite">{{ status }}</p>
  </section>
</template>

<style scoped>
.mini-game { max-width: 26rem; padding-top: .5rem; }
.game-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
h3 { margin: 0; font: 800 2rem/1 var(--font-display); }
button { min-height: 2.5rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }
button:hover, button:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.board-2048 { display: grid; grid-template-columns: repeat(4, 1fr); gap: .25rem; margin: .75rem 0; padding: .25rem; border: 1px solid var(--color-border); background: var(--color-bg); }
.board-2048 span { display: grid; aspect-ratio: 1; place-items: center; background: var(--color-bg-sub); color: var(--color-text-main); font: 800 clamp(1rem, 6vw, 1.7rem)/1 var(--font-mono); }
.board-2048 span[data-value] { color: var(--color-accent); }
.direction-pad { display: grid; grid-template-columns: repeat(3, 3rem); gap: .3rem; width: fit-content; }
.direction-pad button:first-child { grid-column: 2; }.direction-pad button:nth-child(2) { grid-column: 1; }.direction-pad button:nth-child(3) { grid-column: 2; }.direction-pad button:nth-child(4) { grid-column: 3; }
p { color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
</style>
