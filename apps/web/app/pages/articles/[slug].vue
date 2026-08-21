<script setup lang="ts">
import PublicArticleBody from '~/components/articles/PublicArticleBody.vue'
import type { PublicArticleContent } from '~/utils/publicArticleContent'
import { buildPublicArticleSeo } from '~/utils/publicArticleSeo'

const route = useRoute()
const config = useRuntimeConfig()
const { data, pending, error } = await usePublicApi<PublicArticleContent>(`/_content/articles/${encodeURIComponent(String(route.params.slug))}`, { baseURL: config.public.apiBase })
if (error.value?.statusCode === 404 || (!pending.value && !data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Article not found', fatal: true })
}
const requestUrl = useRequestURL()
useHead(() => {
  if (!data.value) return {}
  const seo = buildPublicArticleSeo(data.value.article, requestUrl.origin)
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
      <PublicArticleBody :content="data" />
      <nav v-if="data.toc.length" class="article-toc" aria-label="文章航标">
        <span>TOC / NAV</span>
        <ol>
          <li v-for="item in data.toc" :key="item.id"><a :href="`#${item.id}`">{{ item.label }}</a></li>
        </ol>
      </nav>
    </template>
  </section>
</template>
