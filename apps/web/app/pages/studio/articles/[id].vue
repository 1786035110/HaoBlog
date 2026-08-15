<template>
  <p v-if="loading" class="signal-note">正在锁定文章信号…</p>
  <p v-else-if="loadError" class="form-error" role="alert">{{ loadError }}</p>
  <LazyEditor v-else-if="article" :article="article" :categories="categories" :tags="tags" :save-article="save" />
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { formToUpdateRequest, type ArticleFormModel } from '../../../utils/studioArticleForm'

definePageMeta({ layout: 'studio' })

const route = useRoute()
const LazyEditor = defineAsyncComponent(() => import('../../../components/studio/StudioArticleEditor.client.vue'))
const { getArticle, listCategories, listTags, updateArticle } = useAdminContent()
const article = ref<components['schemas']['AdminArticleResponse'] | null>(null)
const categories = ref<components['schemas']['CategoryResponse'][]>([])
const tags = ref<components['schemas']['TagResponse'][]>([])
const loading = ref(true)
const loadError = ref('')

async function load() {
  try {
    ;[article.value, categories.value, tags.value] = await Promise.all([
      getArticle(String(route.params.id)),
      listCategories(),
      listTags(),
    ])
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '文章暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function save(form: ArticleFormModel) {
  if (!article.value) throw new Error('文章尚未加载。')
  const saved = await updateArticle(article.value.id, formToUpdateRequest(form))
  article.value = saved
  return saved
}

onMounted(load)
</script>

<style scoped>
.signal-note, .form-error { max-width: 52rem; margin: 10vh auto; font: var(--text-sm)/1.5 var(--font-mono); color: var(--color-text-muted); }
.form-error { color: var(--color-warn); }
</style>
