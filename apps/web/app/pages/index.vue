<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import PublicHomeOverview from '~/components/home/PublicHomeOverview.vue'

type Site = components['schemas']['SiteResponse']
type ArticleList = components['schemas']['ArticleListResponse']

const defaultSite: Site = {
  title: 'HaoBlog',
  description: '极夜观测站',
  siteUrl: 'http://localhost:3000',
  authorName: 'Hao',
}
const defaultRecent: ArticleList = { items: [], page: 0, size: 5, total: 0 }
const [{ data: site, error: siteError }, { data: recent, error: recentError }] = await Promise.all([
  usePublicApi<Site>('/api/v1/public/site', { default: () => defaultSite }),
  usePublicApi<ArticleList>('/api/v1/public/articles', { query: { page: 0, size: 5 }, default: () => defaultRecent }),
])
</script>

<template>
  <PublicHomeOverview
    :site="site || defaultSite"
    :recent="recent || defaultRecent"
    :site-error="Boolean(siteError)"
    :recent-error="Boolean(recentError)"
  />
</template>
