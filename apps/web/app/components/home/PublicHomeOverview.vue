<script setup lang="ts">
import type { components } from '@haoblog/api-client'

type Site = components['schemas']['SiteResponse']
type ArticleList = components['schemas']['ArticleListResponse']

defineProps<{
  site: Site
  recent: ArticleList
  siteError?: boolean
  recentError?: boolean
}>()

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value)).replaceAll('/', '.')
}
</script>

<template>
  <section class="home-overview observation-scene" aria-labelledby="site-title">
    <p class="instrument-label">OBSERVATION / INITIAL FRAME</p>
    <h1 id="site-title">{{ site.title }}</h1>
    <p class="signal-copy">{{ site.description }}</p>
    <p v-if="siteError" class="signal-note" role="alert">站点信号暂时不可用，当前显示基础信息。</p>

    <section class="recent-observations" aria-labelledby="recent-title">
      <p class="instrument-label">RECENT / PUBLIC LOG</p>
      <h2 id="recent-title">近期观测</h2>
      <p v-if="recentError" class="signal-note" role="alert">近期文章信号暂时不可用，请稍后重试。</p>
      <p v-else-if="recent.items.length === 0" class="signal-note">当前没有已锁定的公开文章。</p>
      <ol v-else class="recent-list">
        <li v-for="article in recent.items" :key="article.id">
          <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
          <NuxtLink :to="`/articles/${article.slug}`">{{ article.title }}</NuxtLink>
        </li>
      </ol>
      <NuxtLink class="recent-more" to="/articles">查看全部文章 →</NuxtLink>
    </section>
  </section>
</template>
