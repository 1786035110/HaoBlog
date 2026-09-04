<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { shouldIgnoreGameKey } from '../../utils/errorGames'

type Direction = 'left' | 'right' | 'up' | 'down'
const size = 14
const snake = ref<number[]>([])
const food = ref(0)
const direction = ref<Direction>('right')
const running = ref(false)
const gameOver = ref(false)
const status = ref('按开始后使用方向键、WASD 或屏幕方向键。')
let timer: ReturnType<typeof setInterval> | null = null
const score = computed(() => Math.max(0, snake.value.length - 3))

function placeFood() {
  const occupied = new Set(snake.value)
  const available = Array.from({ length: size * size }, (_, index) => index).filter(index => !occupied.has(index))
  food.value = available[Math.floor(Math.random() * available.length)] ?? 0
}

function reset() {
  stop()
  const middle = Math.floor(size / 2) * size + Math.floor(size / 2)
  snake.value = [middle, middle - 1, middle - 2]
  direction.value = 'right'
  gameOver.value = false
  status.value = '准备好了，按开始。'
  placeFood()
}

function start() {
  if (gameOver.value) reset()
  if (running.value) return
  running.value = true
  status.value = `游戏进行中，得分 ${score.value}。`
  timer = setInterval(tick, 145)
}

function stop() {
  running.value = false
  if (timer) clearInterval(timer)
  timer = null
}

function pause() {
  stop()
  status.value = '已暂停。'
}

function changeDirection(next: Direction) {
  const opposite = { left: 'right', right: 'left', up: 'down', down: 'up' } as const
  if (next !== opposite[direction.value]) direction.value = next
}

function tick() {
  const head = snake.value[0]!
  const row = Math.floor(head / size)
  const column = head % size
  const nextRow = row + (direction.value === 'up' ? -1 : direction.value === 'down' ? 1 : 0)
  const nextColumn = column + (direction.value === 'left' ? -1 : direction.value === 'right' ? 1 : 0)
  const next = nextRow * size + nextColumn
  const eating = next === food.value
  const collisionBody = eating ? snake.value : snake.value.slice(0, -1)
  if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size || collisionBody.includes(next)) {
    stop()
    gameOver.value = true
    status.value = `游戏结束，得分 ${score.value}。`
    return
  }
  const nextSnake = [next, ...snake.value]
  if (eating) {
    snake.value = nextSnake
    placeFood()
    status.value = `得分 ${score.value}。`
  } else snake.value = nextSnake.slice(0, -1)
}

function onKeydown(event: KeyboardEvent) {
  if (shouldIgnoreGameKey(event)) return
  const next = ({ ArrowLeft: 'left', a: 'left', A: 'left', ArrowRight: 'right', d: 'right', D: 'right', ArrowUp: 'up', w: 'up', W: 'up', ArrowDown: 'down', s: 'down', S: 'down' } as Record<string, Direction>)[event.key]
  if (!next) return
  event.preventDefault()
  changeDirection(next)
}

onMounted(() => { reset(); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => { stop(); window.removeEventListener('keydown', onKeydown) })
</script>

<template>
  <section class="mini-game" aria-labelledby="snake-title">
    <div class="game-head">
      <div><h3 id="snake-title">贪吃蛇</h3><span>SCORE {{ score }}</span></div>
      <div><button v-if="!running" type="button" @click="start">{{ gameOver ? '再来一局' : '开始' }}</button><button v-else type="button" @click="pause">暂停</button></div>
    </div>
    <div class="snake-board" aria-hidden="true">
      <span v-for="index in size * size" :key="index" :class="{ snake: snake.includes(index - 1), head: snake[0] === index - 1, food: food === index - 1 }" />
    </div>
    <div class="direction-pad" aria-label="移动方向">
      <button type="button" aria-label="向上" @click="changeDirection('up')">↑</button>
      <button type="button" aria-label="向左" @click="changeDirection('left')">←</button>
      <button type="button" aria-label="向下" @click="changeDirection('down')">↓</button>
      <button type="button" aria-label="向右" @click="changeDirection('right')">→</button>
    </div>
    <p role="status" aria-live="polite">{{ status }}</p>
  </section>
</template>

<style scoped>
.mini-game { max-width: 26rem; padding-top: .5rem; }
.game-head, .game-head > div { display: flex; align-items: center; justify-content: space-between; gap: .6rem; }.game-head > div:first-child { align-items: baseline; }
h3 { margin: 0; font: 800 2rem/1 var(--font-display); }.game-head span { color: var(--color-accent); font: var(--text-xs)/1 var(--font-mono); }
button { min-height: 2.5rem; padding-inline: .7rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }
button:hover, button:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
.snake-board { display: grid; grid-template-columns: repeat(14, 1fr); gap: 1px; aspect-ratio: 1; margin: .75rem 0; padding: 2px; border: 1px solid var(--color-border); background: var(--color-border); }
.snake-board span { background: var(--color-bg-sub); }.snake-board .snake { background: color-mix(in srgb, var(--color-accent) 55%, var(--color-bg-sub)); }.snake-board .head { background: var(--color-accent); }.snake-board .food { background: var(--color-warn); }
.direction-pad { display: grid; grid-template-columns: repeat(3, 3rem); gap: .3rem; width: fit-content; }.direction-pad button:first-child { grid-column: 2; }.direction-pad button:nth-child(2) { grid-column: 1; }.direction-pad button:nth-child(3) { grid-column: 2; }.direction-pad button:nth-child(4) { grid-column: 3; }
p { color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
</style>
