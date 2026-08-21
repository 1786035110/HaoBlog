<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { useDataSaver } from '../../composables/useDataSaver'

type Site = components['schemas']['SiteResponse']
type ArticleList = components['schemas']['ArticleListResponse']

const { enabled: dataSaver } = useDataSaver()

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
  <section class="home-overview observation-scene" :class="{ 'is-data-saver': dataSaver }" aria-labelledby="site-title">
    <div class="home-calibration">
      <div>
        <p class="instrument-label">OBSERVATION / INITIAL FRAME</p>
        <p class="home-readout"><span class="status-light" aria-hidden="true" /> PUBLIC SSR / SIGNAL LOCKED</p>
      </div>
      <p class="home-coordinate">N 31°14′ · E 121°28′<br>UTC+08 / NIGHT SHIFT</p>
    </div>
    <h1 id="site-title">{{ site.title }}</h1>
    <p class="home-identity">{{ site.authorName }} / DEVELOPER OBSERVATORY</p>
    <p class="signal-copy">{{ site.description }}</p>
    <p v-if="siteError" class="signal-note" role="alert">站点信号暂时不可用，当前显示基础信息。</p>
    <a class="continue-reading" href="#recent-title"><span aria-hidden="true">↓</span> 继续阅读 / CONTINUE READING</a>

    <section class="recent-observations" aria-labelledby="recent-title">
      <div class="recent-heading">
        <div>
          <p class="instrument-label">RECENT / PUBLIC LOG</p>
          <h2 id="recent-title">最近公开文章</h2>
        </div>
        <span class="log-count">{{ String(recent.total).padStart(2, '0') }} ENTRIES</span>
      </div>
      <p v-if="recentError" class="signal-note" role="alert">近期文章信号暂时不可用，请稍后重试。</p>
      <p v-else-if="recent.items.length === 0" class="signal-note">当前没有已锁定的公开文章。</p>
      <ol v-else class="recent-list">
        <li v-for="(article, index) in recent.items" :key="article.id">
          <span class="recent-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
          <NuxtLink :to="`/articles/${article.slug}`">{{ article.title }}<small>{{ article.excerpt || '信号已锁定，正文可读。' }}</small></NuxtLink>
        </li>
      </ol>
      <NuxtLink class="recent-more" to="/articles">查看全部文章 →</NuxtLink>
    </section>
  </section>
</template>
