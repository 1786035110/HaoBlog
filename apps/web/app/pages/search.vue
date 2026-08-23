<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import PublicArticleList from '~/components/articles/PublicArticleList.vue'
import { buildPublicPageSeo, publicPageHead } from '~/utils/publicArticleSeo'
import { isPublicSearchPageOutOfRange, normalizePublicSearchQuery, publicSearchPageUrl } from '~/utils/publicSearch'
import { parsePublicArticlePage } from '~/utils/publicArticlePagination'
import { defaultPublicSite, usePublicSite } from '~/utils/publicSite'

type ArticleList = components['schemas']['ArticleListResponse']
const emptyResult: ArticleList = { items: [], page: 0, size: 20, total: 0 }
const route = useRoute()
const rawQuery = typeof route.query.q === 'string' ? route.query.q : ''
const query = normalizePublicSearchQuery(rawQuery)
const hasQuery = rawQuery.length > 0
const invalidQuery = hasQuery && !query
const parsedPage = parsePublicArticlePage(route.query.page)

if (!parsedPage) {
  throw createError({ statusCode: 404, statusMessage: 'Search page not found', fatal: true })
}

const page = parsedPage.page
const [{ data: site }, search] = await Promise.all([
  usePublicSite(),
  query && !invalidQuery
    ? usePublicApi<ArticleList>('/api/v1/public/search/articles', { query: { q: query, page: page - 1, size: 20 } })
    : Promise.resolve({ data: ref(emptyResult), pending: ref(false), error: ref(null) }),
])

if (query && !invalidQuery && !search.pending.value && !search.error.value && search.data.value && isPublicSearchPageOutOfRange(page, search.data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Search page not found', fatal: true })
}

const resolvedSite = site.value || defaultPublicSite
const searchPath = query ? publicSearchPageUrl(query, page) : '/search'
useHead(() => publicPageHead(
  buildPublicPageSeo(resolvedSite, searchPath, query ? `搜索「${query}」 · ${resolvedSite.title}` : `文章搜索 · ${resolvedSite.title}`, '在已发布的文章快照中检索标题、摘要和正文。'),
  'noindex,follow',
))
</script>

<template>
  <section class="search-scene observation-scene" aria-labelledby="search-title">
    <p class="instrument-label">SEARCH ARRAY / PUBLIC SNAPSHOTS</p>
    <h1 id="search-title">文章搜索</h1>
    <p class="signal-copy">只检索已经锁定的公开版本，标题、摘要和正文按命中层级排列。</p>

    <form class="search-signal-form" action="/search" method="get">
      <label for="search-query">检索词</label>
      <div class="search-signal-controls">
        <input id="search-query" name="q" type="search" :value="rawQuery" minlength="2" maxlength="100" required autocomplete="off" placeholder="中文 / PostgreSQL / pg_trgm">
        <button type="submit">SEARCH →</button>
      </div>
    </form>

    <p v-if="invalidQuery" class="signal-note" role="alert">请输入 2–100 个 Unicode 字符。</p>
    <p v-else-if="!query" class="signal-note">输入至少 2 个字符，开始观测。</p>
    <template v-else>
      <p class="search-readout"><span class="status-light" aria-hidden="true" /> QUERY / {{ query }} / {{ search.data?.value?.total || 0 }} HITS</p>
      <p v-if="search.pending?.value" class="signal-note" role="status">正在接收搜索信号…</p>
      <p v-else-if="search.error?.value" class="signal-note" role="alert">搜索信号暂时不可用，请稍后重试。</p>
      <PublicArticleList
        v-else-if="search.data?.value"
        :result="search.data.value"
        :page="page"
        :search-query="query"
        empty-message="没有匹配的公开文章。"
      />
    </template>
  </section>
</template>
