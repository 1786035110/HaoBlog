<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{ completed: []; failed: [] }>()
const segments = ['A / 稳压段', 'B / 回路段', 'C / 输出段']
const expected = [0, 1, 2]
const nextIndex = ref(0)
const attempts = ref(0)
const state = ref<'active' | 'success' | 'failed'>('active')
const status = ref('按顺序接通 A、B、C 三个电路段。最多尝试 3 次。')
const attemptsLeft = computed(() => Math.max(0, 3 - attempts.value))

function choose(index: number) {
  if (state.value !== 'active') return
  if (index !== expected[nextIndex.value]) {
    attempts.value += 1
    nextIndex.value = 0
    if (attempts.value >= 3) {
      state.value = 'failed'
      status.value = '修复尝试已耗尽，已切换到搜索降级。'
      emit('failed')
    } else {
      status.value = `断点仍在第 ${attempts.value + 1} 次尝试；请从 A 重新接通。还可尝试 ${attemptsLeft.value} 次。`
    }
    return
  }
  nextIndex.value += 1
  if (nextIndex.value === expected.length) {
    state.value = 'success'
    status.value = '三段电路已导通，丢失信号已修复。'
    emit('completed')
  } else {
    const target = expected[nextIndex.value] ?? 0
    status.value = `第 ${index + 1} 段已导通，请继续选择 ${segments[target]}。`
  }
}

</script>

<template>
  <section class="repair-game" aria-labelledby="repair-game-title">
    <div class="repair-heading">
      <div>
        <p class="instrument-label">SIGNAL REPAIR / THREE SEGMENTS</p>
        <h2 id="repair-game-title">丢失信号修复</h2>
      </div>
      <span class="repair-attempts">剩余尝试 {{ attemptsLeft }} / 3</span>
    </div>
    <p class="repair-instruction">键盘 Tab 聚焦后按 Enter 或 Space 接通电路。状态会同步播报。</p>
    <div class="repair-circuit" role="group" aria-label="三个电路段">
      <template v-for="(segment, index) in segments" :key="segment">
        <button class="repair-segment" :class="{ 'is-connected': index < nextIndex && state === 'active' || state === 'success' }" type="button" :data-segment="index" :aria-label="segment" :disabled="state !== 'active'" @click="choose(index)">{{ segment }}</button>
        <span v-if="index < segments.length - 1" class="repair-wire" aria-hidden="true">——</span>
      </template>
    </div>
    <p class="repair-status" role="status" aria-live="polite">{{ status }}</p>
    <div class="repair-actions"><button v-if="state === 'active'" type="button" class="repair-skip" @click="emit('failed')">跳过小游戏</button></div>
  </section>
</template>

<style scoped>
.repair-game { margin-top: 2.5rem; padding: 1rem 0 1.25rem; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.repair-heading { display: flex; align-items: end; justify-content: space-between; gap: 1rem; }
.repair-heading h2 { margin: .45rem 0 0; font: 700 clamp(1.5rem, 5vw, 2.5rem)/1 var(--font-display); }
.repair-attempts, .repair-instruction, .repair-status, .repair-skip, .repair-segment { font: var(--text-xs)/1.5 var(--font-mono); }
.repair-attempts, .repair-instruction, .repair-status { color: var(--color-text-muted); }
.repair-instruction { margin: .9rem 0; }
.repair-circuit { display: flex; align-items: center; gap: .4rem; }
.repair-segment { min-width: 7rem; padding: .8rem .55rem; border: 1px solid var(--color-border); background: var(--color-bg-sub); color: var(--color-text-main); cursor: pointer; }
.repair-segment:hover, .repair-segment:focus-visible, .repair-segment.is-connected { border-color: var(--color-accent); color: var(--color-accent); }
.repair-segment:disabled { cursor: default; }
.repair-wire { color: var(--color-text-muted); }
.repair-status { min-height: 1.6em; margin: .9rem 0 .5rem; }
.repair-actions { display: flex; gap: .6rem; }
.repair-skip { padding: .45rem .6rem; border: 1px solid var(--color-border); background: transparent; color: var(--color-accent); cursor: pointer; }
@media (max-width: 640px) { .repair-heading { align-items: start; flex-direction: column; }.repair-circuit { align-items: stretch; flex-direction: column; }.repair-wire { display: none; }.repair-segment { width: 100%; } }
</style>
