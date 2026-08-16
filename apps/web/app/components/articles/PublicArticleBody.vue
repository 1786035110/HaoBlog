<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import SafeMarkdown from './SafeMarkdown.vue'

type Article = components['schemas']['ArticleResponse']
defineProps<{ article: Article }>()

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'long',
  timeZone: 'Asia/Shanghai',
}).format(new Date(value))
</script>

<template>
  <article class="article-reading">
    <p class="instrument-label">SIGNAL / ARTICLE</p>
    <h1 id="article-title">{{ article.title }}</h1>
    <p class="article-meta"><time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time></p>
    <p v-if="article.excerpt" class="article-excerpt">{{ article.excerpt }}</p>
    <SafeMarkdown :markdown="article.markdown" />
  </article>
</template>
