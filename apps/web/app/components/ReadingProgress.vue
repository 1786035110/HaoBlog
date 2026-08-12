<template><div class="dock-progress" :style="{ transform: `scaleX(${progress})` }" aria-hidden="true" /></template>

<script setup lang="ts">
const progress = ref(0)
let frame = 0
const update = () => {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const max = document.documentElement.scrollHeight - window.innerHeight
    progress.value = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0
  })
}
onMounted(() => {
  update()
  window.addEventListener('scroll', update, { passive: true })
  window.addEventListener('resize', update, { passive: true })
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', update)
  window.removeEventListener('resize', update)
  if (frame) cancelAnimationFrame(frame)
})
</script>
