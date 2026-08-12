<script setup lang="ts">
import type { components } from '@haoblog/api-client'

type ArticleList = components['schemas']['ArticleListResponse']
const { data, pending, error } = await usePublicApi<ArticleList>('/api/v1/public/articles')
const items = computed(() => data.value?.items ?? [])
const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
</script>

<template>
  <section class="article-index observation-scene" aria-labelledby="articles-title">
    <p class="instrument-label">OBSERVATION LOG / ARTICLES</p>
    <h1 id="articles-title">文章观测日志</h1>
    <p class="signal-copy">沿着时间线阅读正在演化的技术记录。</p>
    <p v-if="pending" class="signal-note" role="status">正在接收文章信号…</p>
    <p v-else-if="error" class="signal-note" role="alert">文章信号暂时不可用，请稍后重试。</p>
    <p v-else-if="items.length === 0" class="signal-note">当前没有已锁定的公开文章。</p>
    <ol v-else class="observation-timeline">
      <li v-for="article in items" :key="article.id" class="observation-entry">
        <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
        <div>
          <NuxtLink :to="`/articles/${article.slug}`"><h2>{{ article.title }}</h2></NuxtLink>
          <p>{{ article.excerpt || '暂无摘要。' }}</p>
        </div>
      </li>
    </ol>
  </section>
</template>
