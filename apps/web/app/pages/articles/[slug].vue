<script setup lang="ts">
import type { components } from '@haoblog/api-client'

type Article = components['schemas']['ArticleResponse']
const route = useRoute()
const { data, pending, error } = await usePublicApi<Article>(`/api/v1/public/articles/${encodeURIComponent(String(route.params.slug))}`)
if (error.value?.statusCode === 404 || (!pending.value && !data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Article not found', fatal: true })
}
const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(new Date(value))
useHead(() => data.value ? { title: data.value.title, meta: [{ name: 'description', content: data.value.excerpt || data.value.title }] } : {})
</script>

<template>
  <section class="article-instrument observation-scene" aria-labelledby="article-title">
    <p v-if="pending" class="signal-note" role="status">正在锁定文章信号…</p>
    <p v-else-if="error" class="signal-note" role="alert">文章信号暂时不可用。</p>
    <template v-else-if="data">
      <aside class="article-signal" aria-label="阅读进度">SIGNAL / READ</aside>
      <article class="article-reading">
        <p class="instrument-label">SIGNAL / ARTICLE</p>
        <h1 id="article-title">{{ data.title }}</h1>
        <p class="article-meta"><time :datetime="data.publishedAt">{{ formatDate(data.publishedAt) }}</time></p>
        <p v-if="data.excerpt" class="article-excerpt">{{ data.excerpt }}</p>
        <SafeMarkdown :markdown="data.markdown" />
      </article>
      <aside class="article-toc" aria-label="文章航标"><span>TOC / NAV</span><p>正文结构将在后续内容阶段增强。</p></aside>
    </template>
  </section>
</template>
