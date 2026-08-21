<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import PublicArticleList from '~/components/articles/PublicArticleList.vue'
import { isPublicArticlePageOutOfRange, parsePublicArticlePage } from '~/utils/publicArticlePagination'

type ArticleList = components['schemas']['ArticleListResponse']
const route = useRoute()
const parsedPage = parsePublicArticlePage(route.query.page)

if (!parsedPage) {
  throw createError({ statusCode: 404, statusMessage: 'Article page not found', fatal: true })
}
if (parsedPage.canonical) {
  await navigateTo('/articles', { redirectCode: 301 })
}

const page = parsedPage.page
const pageSize = 20
const { data, pending, error } = await usePublicApi<ArticleList>('/api/v1/public/articles', {
  query: { page: page - 1, size: pageSize },
})

if (!pending.value && !error.value && data.value && isPublicArticlePageOutOfRange(page, data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Article page not found', fatal: true })
}
</script>

<template>
  <section class="article-index observation-scene" aria-labelledby="articles-title">
    <p class="instrument-label">OBSERVATION LOG / ARTICLES</p>
    <h1 id="articles-title">文章观测日志</h1>
    <p class="signal-copy">沿着时间线阅读正在演化的技术记录。</p>
    <p class="index-note"><span class="status-light" aria-hidden="true" /> 按发布时间排列 / PUBLIC SIGNALS ONLY</p>
    <p v-if="pending" class="signal-note" role="status">正在接收文章信号…</p>
    <p v-else-if="error" class="signal-note" role="alert">文章信号暂时不可用，请稍后重试。</p>
    <PublicArticleList v-else-if="data" :result="data" :page="page" />
  </section>
</template>
