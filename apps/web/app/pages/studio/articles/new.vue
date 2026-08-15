<template>
  <p v-if="loading" class="signal-note">正在准备新建工作台…</p>
  <p v-else-if="loadError" class="form-error" role="alert">{{ loadError }}</p>
  <LazyEditor v-else ref="editor" :categories="categories" :tags="tags" :saving="saving" :server-error="saveError" @save="save" />
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { formToCreateRequest, type ArticleFormModel } from '../../../utils/studioArticleForm'

definePageMeta({ layout: 'studio' })

const LazyEditor = defineAsyncComponent(() => import('../../../components/studio/StudioArticleEditor.client.vue'))
const { listCategories, listTags, createArticle } = useAdminContent()
const categories = ref<components['schemas']['CategoryResponse'][]>([])
const tags = ref<components['schemas']['TagResponse'][]>([])
const loading = ref(true)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const editor = ref<{ markSaved(article: components['schemas']['AdminArticleResponse']): void } | null>(null)

async function load() {
  try {
    ;[categories.value, tags.value] = await Promise.all([listCategories(), listTags()])
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '分类和标签暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function save(form: ArticleFormModel) {
  saving.value = true
  saveError.value = ''
  try {
    const article = await createArticle(formToCreateRequest(form))
    editor.value?.markSaved(article)
    await navigateTo(`/studio/articles/${article.id}`)
  } catch (cause) {
    saveError.value = cause instanceof Error ? cause.message : '文章创建失败。'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.signal-note, .form-error { max-width: 52rem; margin: 10vh auto; font: var(--text-sm)/1.5 var(--font-mono); color: var(--color-text-muted); }
.form-error { color: var(--color-warn); }
</style>
