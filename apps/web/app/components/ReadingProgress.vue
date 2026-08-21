<template><div class="dock-progress" :style="{ transform: `scaleX(${progress})` }" aria-hidden="true" /></template>

<script setup lang="ts">
import { useScrollProgress } from '../composables/useScrollProgress'

const { progress } = useScrollProgress()
</script>
