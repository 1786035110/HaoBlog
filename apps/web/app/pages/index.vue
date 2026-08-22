<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import PublicHomeOverview from '~/components/home/PublicHomeOverview.vue'
type ArticleList = components['schemas']['ArticleListResponse']
import { buildPublicPageSeo, publicPageHead } from '~/utils/publicArticleSeo'
import { defaultPublicSite, usePublicSite } from '~/utils/publicSite'

const defaultRecent: ArticleList = { items: [], page: 0, size: 5, total: 0 }
const [{ data: site, error: siteError }, { data: recent, error: recentError }] = await Promise.all([
  usePublicSite(),
  usePublicApi<ArticleList>('/api/v1/public/articles', { query: { page: 0, size: 5 }, default: () => defaultRecent }),
])

useHead(() => publicPageHead(buildPublicPageSeo(site.value || defaultPublicSite, '/', site.value?.title || defaultPublicSite.title, site.value?.description || defaultPublicSite.description)))
</script>

<template>
  <PublicHomeOverview
    :site="site || defaultPublicSite"
    :recent="recent || defaultRecent"
    :site-error="Boolean(siteError)"
    :recent-error="Boolean(recentError)"
  />
</template>
