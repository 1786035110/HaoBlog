<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import SafeMarkdown from '~/components/articles/SafeMarkdown.vue'

type Preview = components['schemas']['ArticlePreviewResponse']
const route = useRoute()
const token = String(route.params.token)
const { data, pending, error } = await usePublicApi<Preview>(`/api/v1/public/article-previews/${encodeURIComponent(token)}`, {
  key: `article-preview:${token}`,
  watch: false,
})
if (error.value?.statusCode === 404 || error.value?.statusCode === 410 || (!pending.value && !data.value)) {
  throw createError({ statusCode: error.value?.statusCode || 404, statusMessage: 'Preview not found', fatal: true })
}
useHead(() => data.value ? {
  title: data.value.seoTitle?.trim() || data.value.title,
  meta: [
    { name: 'description', content: data.value.seoDescription?.trim() || data.value.excerpt || data.value.title },
    { name: 'robots', content: 'noindex,nofollow' },
    { property: 'og:title', content: data.value.seoTitle?.trim() || data.value.title },
  ],
} : { meta: [{ name: 'robots', content: 'noindex,nofollow' }] })
</script>

<template>
  <section class="article-instrument observation-scene" aria-labelledby="preview-title">
    <p v-if="pending" class="signal-note" role="status">正在锁定预览信号…</p>
    <p v-else-if="error" class="signal-note" role="alert">预览信号暂时不可用。</p>
    <article v-else-if="data" class="article-reading">
      <p class="instrument-label">SIGNAL / PREVIEW</p>
      <h1 id="preview-title">{{ data.title }}</h1>
      <p class="article-meta">限时预览 · 工作版本 {{ data.version }}</p>
      <p v-if="data.excerpt" class="article-excerpt">{{ data.excerpt }}</p>
      <SafeMarkdown :markdown="data.markdown" />
    </article>
  </section>
</template>
