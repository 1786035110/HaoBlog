<template>
  <p v-if="loading" class="signal-note">正在建立新文章信号…</p>
  <p v-else-if="loadError" class="form-error" role="alert">{{ loadError }}</p>
  <button v-else class="instrument-button" type="button" @click="load">重试建立文章</button>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

definePageMeta({ layout: 'studio' })

const { createArticle } = useAdminContent()
const loading = ref(true)
const loadError = ref('')
const createdArticleId = ref('')

async function load() {
  if (createdArticleId.value) return navigateTo(`/studio/articles/${createdArticleId.value}`, { replace: true })
  loading.value = true
  loadError.value = ''
  try {
    const article = await createArticle({})
    createdArticleId.value = article.id
    await navigateTo(`/studio/articles/${article.id}`, { replace: true })
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '新文章暂时无法建立。'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.signal-note, .form-error { max-width: 52rem; margin: 10vh auto; font: var(--text-sm)/1.5 var(--font-mono); color: var(--color-text-muted); }
.form-error { color: var(--color-warn); }
.instrument-button { display: block; margin: 10vh auto; padding: .7rem 1rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }
</style>
