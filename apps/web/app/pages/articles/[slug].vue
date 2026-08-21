<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PublicArticleBody from '~/components/articles/PublicArticleBody.vue'
import type { PublicArticleContent } from '~/utils/publicArticleContent'
import { buildPublicArticleSeo } from '~/utils/publicArticleSeo'

const route = useRoute()
const config = useRuntimeConfig()
const { data, pending, error } = await usePublicApi<PublicArticleContent>(`/_content/articles/${encodeURIComponent(String(route.params.slug))}`, { baseURL: config.public.apiBase })
if (error.value?.statusCode === 404 || (!pending.value && !data.value)) {
  throw createError({ statusCode: 404, statusMessage: 'Article not found', fatal: true })
}
const articleBody = ref<{ rootElement: HTMLElement | null } | null>(null)
const activeTocId = ref<string | null>(null)
const { articleProgress } = useScrollProgress()
let headingObserver: IntersectionObserver | null = null

watch(() => data.value?.toc, toc => {
  activeTocId.value = toc?.[0]?.id ?? null
}, { immediate: true })

function observeHeadings() {
  headingObserver?.disconnect()
  headingObserver = null
  const root = articleBody.value?.rootElement
  if (!root || !data.value?.toc.length || typeof IntersectionObserver === 'undefined') return
  const visible = new Map<Element, number>()
  headingObserver = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) visible.set(entry.target, entry.boundingClientRect.top)
      else visible.delete(entry.target)
    }
    const current = [...visible.entries()].sort((left, right) => left[1] - right[1])[0]?.[0]
    const id = current instanceof HTMLElement ? current.id : undefined
    if (id) activeTocId.value = id
  }, { rootMargin: '-12% 0px -70% 0px', threshold: [0, 1] })
  root.querySelectorAll('h2, h3').forEach(heading => headingObserver?.observe(heading))
}

onMounted(() => { void nextTick(observeHeadings) })
watch(() => data.value?.renderedHtml, () => { void nextTick(observeHeadings) })
onBeforeUnmount(() => headingObserver?.disconnect())

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
      <aside class="article-signal" aria-label="文章阅读进度">
        <span class="article-signal-label">SIGNAL / READ</span>
        <span class="article-signal-track" role="progressbar" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="Math.round(articleProgress * 100)" :aria-valuetext="`已阅读 ${Math.round(articleProgress * 100)}%`">
          <span class="article-signal-fill" :style="{ '--signal-progress': `${Math.round(articleProgress * 100)}%` }" />
        </span>
        <span class="article-signal-value">{{ Math.round(articleProgress * 100) }}%</span>
      </aside>
      <PublicArticleBody ref="articleBody" :content="data" />
      <nav v-if="data.toc.length" class="article-toc" aria-label="文章航标">
        <details open>
          <summary>TOC / NAV <span aria-hidden="true">{{ data.toc.length }}</span></summary>
          <ol>
            <li v-for="item in data.toc" :key="item.id" :data-level="item.level">
              <a :class="{ 'is-current': activeTocId === item.id }" :href="`#${item.id}`" :aria-current="activeTocId === item.id ? 'location' : undefined">{{ item.label }}</a>
            </li>
          </ol>
        </details>
      </nav>
    </template>
  </section>
</template>
