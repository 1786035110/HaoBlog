<script setup lang="ts">
import type { PublicArticleContent } from '~/utils/publicArticleContent'
import MermaidEnhancer from './MermaidEnhancer.vue'

const props = defineProps<{ content: PublicArticleContent }>()

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'long',
  timeZone: 'Asia/Shanghai',
}).format(new Date(value))
</script>

<template>
  <article class="article-reading">
    <p class="instrument-label">SIGNAL / ARTICLE</p>
    <h1 id="article-title">{{ props.content.article.title }}</h1>
    <p class="article-meta"><time :datetime="props.content.article.publishedAt">{{ formatDate(props.content.article.publishedAt) }}</time></p>
    <p v-if="props.content.article.excerpt" class="article-excerpt">{{ props.content.article.excerpt }}</p>
    <div class="safe-markdown">
      <div v-html="props.content.renderedHtml" />
      <MermaidEnhancer v-if="props.content.hasMermaid" />
    </div>
  </article>
</template>
