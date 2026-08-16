<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { buildPublicArticleSeo } from '~/utils/publicArticleSeo'

type Article = components['schemas']['ArticleResponse']
const route = useRoute()
const { data, pending, error } = await usePublicApi<Article>(`/api/v1/public/articles/${encodeURIComponent(String(route.params.slug))}`)
if (error.value?.statusCode === 404 || (!pending.value && !data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Article not found', fatal: true })
}
const requestUrl = useRequestURL()
useHead(() => {
  if (!data.value) return {}
  const seo = buildPublicArticleSeo(data.value, requestUrl.origin)
  return {
    title: seo.title,
    meta: [
      { name: 'description', content: seo.description },
      { property: 'og:type', content: 'article' },
      { property: 'og:title', content: seo.title },
      { property: 'og:description', content: seo.description },
      { property: 'og:image', content: seo.image },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: seo.title },
      { name: 'twitter:description', content: seo.description },
      { name: 'twitter:image', content: seo.image },
    ],
  }
})
</script>

<template>
  <section class="article-instrument observation-scene" aria-labelledby="article-title">
    <p v-if="pending" class="signal-note" role="status">正在锁定文章信号…</p>
    <p v-else-if="error" class="signal-note" role="alert">文章信号暂时不可用。</p>
    <template v-else-if="data">
      <aside class="article-signal" aria-label="阅读进度">SIGNAL / READ</aside>
      <PublicArticleBody :article="data" />
      <aside class="article-toc" aria-label="文章航标"><span>TOC / NAV</span><p>正文结构将在后续内容阶段增强。</p></aside>
    </template>
  </section>
</template>
