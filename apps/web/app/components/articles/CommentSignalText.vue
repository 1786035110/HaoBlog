<script setup lang="ts">
import { computed } from 'vue'
import { splitCommentSignalText } from '../../utils/commentSignal'

const props = defineProps<{ text: string }>()
const segments = computed(() => splitCommentSignalText(props.text))
</script>

<template>
  <template v-for="(segment, index) in segments" :key="`${index}-${segment.text}`">
    <a v-if="segment.href" :href="segment.href" target="_blank" rel="noreferrer noopener">{{ segment.text }}</a>
    <span v-else>{{ segment.text }}</span>
  </template>
</template>
