<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { computed } from 'vue'
import { publicArticlePageUrl } from '../../utils/publicArticlePagination'

type ArticleList = components['schemas']['ArticleListResponse']

const props = defineProps<{
  result: ArticleList
  page: number
}>()

const hasPrevious = computed(() => props.page > 1)
const hasNext = computed(() => props.page * props.result.size < props.result.total)

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value)).replaceAll('/', '.')
}

function httpsCoverUrl(value: string | null) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}
</script>

<template>
  <p v-if="result.items.length === 0" class="signal-note">当前没有已锁定的公开文章。</p>
  <template v-else>
    <ol class="observation-timeline" aria-label="公开文章列表">
      <li v-for="article in result.items" :key="article.id" class="observation-entry">
        <time :datetime="article.publishedAt">{{ formatDate(article.publishedAt) }}</time>
        <div class="observation-entry-content">
          <NuxtLink :to="`/articles/${article.slug}`">
            <h2>{{ article.title }}</h2>
          </NuxtLink>
          <div v-if="httpsCoverUrl(article.coverImageUrl)" class="article-cover-frame">
            <img
              :src="httpsCoverUrl(article.coverImageUrl) || undefined"
              :alt="`${article.title} 封面`"
              loading="lazy"
              decoding="async"
            >
          </div>
          <p>{{ article.excerpt || '暂无摘要。' }}</p>
        </div>
      </li>
    </ol>

    <nav v-if="hasPrevious || hasNext" class="article-pagination" aria-label="文章列表分页">
      <NuxtLink v-if="hasPrevious" :to="publicArticlePageUrl(page - 1)">上一页</NuxtLink>
      <span aria-current="page">第 {{ page }} 页</span>
      <NuxtLink v-if="hasNext" :to="publicArticlePageUrl(page + 1)">下一页</NuxtLink>
    </nav>
  </template>
</template>
