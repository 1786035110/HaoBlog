<script setup lang="ts">
import { computed, ref } from 'vue'
import { placeMines } from '../../utils/errorGames'

type Cell = { mine: boolean; adjacent: number; revealed: boolean; flagged: boolean }
const width = 9
const mineCount = 10
const cells = ref<Cell[]>([])
const initialized = ref(false)
const finished = ref(false)
const flagMode = ref(false)
const status = ref('选择一个格子开始；第一次揭开一定安全。')
const flags = computed(() => cells.value.filter(cell => cell.flagged).length)

function neighbors(index: number) {
  const row = Math.floor(index / width)
  const column = index % width
  const result: number[] = []
  for (let rowOffset = -1; rowOffset <= 1; rowOffset += 1) {
    for (let columnOffset = -1; columnOffset <= 1; columnOffset += 1) {
      const nextRow = row + rowOffset
      const nextColumn = column + columnOffset
      if ((rowOffset || columnOffset) && nextRow >= 0 && nextRow < width && nextColumn >= 0 && nextColumn < width) result.push(nextRow * width + nextColumn)
    }
  }
  return result
}

function reset() {
  cells.value = Array.from({ length: width * width }, () => ({ mine: false, adjacent: 0, revealed: false, flagged: false }))
  initialized.value = false
  finished.value = false
  flagMode.value = false
  status.value = '选择一个格子开始；第一次揭开一定安全。'
}

function initialize(safeIndex: number) {
  const mines = placeMines(width * width, mineCount, safeIndex)
  cells.value = cells.value.map((cell, index) => ({ ...cell, mine: mines.has(index), adjacent: neighbors(index).filter(neighbor => mines.has(neighbor)).length }))
  initialized.value = true
}

function reveal(index: number) {
  if (finished.value || cells.value[index]?.flagged) return
  if (!initialized.value) initialize(index)
  const selected = cells.value[index]
  if (!selected) return
  if (selected.mine) {
    cells.value = cells.value.map(cell => cell.mine ? { ...cell, revealed: true } : cell)
    finished.value = true
    status.value = '踩到雷了，重新开始再试一次。'
    return
  }
  const next = cells.value.map(cell => ({ ...cell }))
  const queue = [index]
  const visited = new Set<number>()
  while (queue.length) {
    const current = queue.shift()!
    const cell = next[current]
    if (!cell || visited.has(current) || cell.flagged || cell.mine) continue
    visited.add(current)
    cell.revealed = true
    if (!cell.adjacent) queue.push(...neighbors(current))
  }
  cells.value = next
  const won = next.every(cell => cell.mine || cell.revealed)
  finished.value = won
  status.value = won ? '全部安全格已揭开，你赢了。' : `已插旗 ${flags.value} / ${mineCount}。`
}

function toggleFlag(index: number) {
  const cell = cells.value[index]
  if (!cell || cell.revealed || finished.value) return
  cell.flagged = !cell.flagged
  cells.value = [...cells.value]
  status.value = `已插旗 ${flags.value} / ${mineCount}。`
}

function activate(index: number) {
  if (flagMode.value) toggleFlag(index)
  else reveal(index)
}

function label(cell: Cell, index: number) {
  if (cell.flagged) return `第 ${index + 1} 格，已插旗`
  if (!cell.revealed) return `第 ${index + 1} 格，未揭开`
  if (cell.mine) return `第 ${index + 1} 格，地雷`
  return `第 ${index + 1} 格，周围 ${cell.adjacent} 个雷`
}

reset()
</script>

<template>
  <section class="mini-game" aria-labelledby="mines-title">
    <div class="game-head"><div><h3 id="mines-title">扫雷</h3><span>FLAGS {{ flags }}/{{ mineCount }}</span></div><button type="button" @click="reset">重新开始</button></div>
    <div class="mine-toolbar" role="group" aria-label="触屏操作模式">
      <button type="button" :aria-pressed="!flagMode" @click="flagMode = false">揭开</button>
      <button type="button" :aria-pressed="flagMode" @click="flagMode = true">插旗</button>
    </div>
    <div class="mine-board" role="grid" aria-label="扫雷棋盘">
      <button v-for="(cell, index) in cells" :key="index" type="button" role="gridcell" :class="{ revealed: cell.revealed, mine: cell.revealed && cell.mine, flagged: cell.flagged }" :aria-label="label(cell, index)" @click="activate(index)" @contextmenu.prevent="toggleFlag(index)">
        {{ cell.flagged ? 'F' : cell.revealed && cell.mine ? '×' : cell.revealed && cell.adjacent ? cell.adjacent : '' }}
      </button>
    </div>
    <p role="status" aria-live="polite">{{ status }}</p>
  </section>
</template>

<style scoped>
.mini-game { max-width: 30rem; padding-top: .5rem; }.game-head, .game-head > div { display: flex; align-items: center; justify-content: space-between; gap: .6rem; }.game-head > div { align-items: baseline; }
h3 { margin: 0; font: 800 2rem/1 var(--font-display); }.game-head span { color: var(--color-accent); font: var(--text-xs)/1 var(--font-mono); }
button { min-height: 2.35rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-sub); color: var(--color-text-main); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }button:hover, button:focus-visible, button[aria-pressed='true'] { border-color: var(--color-accent); color: var(--color-accent); }
.mine-toolbar { display: flex; gap: .35rem; margin: .75rem 0 .35rem; }.mine-toolbar button, .game-head > button { padding-inline: .7rem; }
.mine-board { display: grid; grid-template-columns: repeat(9, 1fr); gap: 1px; padding: 2px; border: 1px solid var(--color-border); background: var(--color-border); }.mine-board button { aspect-ratio: 1; min-width: 0; min-height: 0; padding: 0; border: 0; }.mine-board button.revealed { background: color-mix(in srgb, var(--color-text-muted) 12%, var(--color-bg-sub)); }.mine-board button.mine { color: var(--color-warn); }.mine-board button.flagged { color: var(--color-accent); }
p { color: var(--color-text-muted); font: var(--text-xs)/1.5 var(--font-mono); }
</style>
