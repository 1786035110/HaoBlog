<template>
  <p v-if="loading" class="signal-note">正在锁定文章信号…</p>
  <p v-else-if="loadError" class="form-error" role="alert">{{ loadError }}</p>
  <template v-else-if="article">
    <div class="article-tools">
      <NuxtLink class="quiet-button" :to="`/studio/articles/${article.id}/versions`">历史版本 / 比较与恢复</NuxtLink>
    </div>
    <LazyEditor :article="article" :categories="categories" :tags="tags" :save-article="save" :publish-article="publish" :schedule-article="schedule" :create-preview="createPreview" />
  </template>
</template>

<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { formToUpdateRequest, type ArticleFormModel } from '../../../../utils/studioArticleForm'

definePageMeta({ layout: 'studio' })

const route = useRoute()
const LazyEditor = defineAsyncComponent(() => import('../../../../components/studio/StudioArticleEditor.client.vue'))
const { getArticle, listCategories, listTags, updateArticle, publishArticle, scheduleArticle, createPreviewToken } = useAdminContent()
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

async function publish(current: components['schemas']['AdminArticleResponse']) {
  await publishArticle(current.id, current.version)
  return getArticle(current.id)
}

async function schedule(current: components['schemas']['AdminArticleResponse']) {
  if (!current.scheduledAt) throw new Error('请先填写定时发布时间。')
  await scheduleArticle(current.id, current.version, current.scheduledAt)
  return getArticle(current.id)
}

async function createPreview(current: components['schemas']['AdminArticleResponse']) {
  const token = await createPreviewToken(current.id, current.version)
  return `${window.location.origin}/article-previews/${encodeURIComponent(token.token)}`
}

onMounted(load)
</script>

<style scoped>
.signal-note, .form-error { max-width: 52rem; margin: 10vh auto; font: var(--text-sm)/1.5 var(--font-mono); color: var(--color-text-muted); }
.form-error { color: var(--color-warn); }
.article-tools { max-width: 82rem; margin: 0 auto var(--space-4); display: flex; justify-content: end; }
.quiet-button { padding: .6rem .8rem; border: 1px solid var(--color-border); color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); text-decoration: none; }
.quiet-button:hover, .quiet-button:focus-visible { border-color: var(--color-accent); color: var(--color-accent); }
</style>
