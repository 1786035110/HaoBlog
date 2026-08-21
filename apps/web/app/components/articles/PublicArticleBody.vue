<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useScrollProgress } from '../../composables/useScrollProgress'
import type { PublicArticleContent } from '~/utils/publicArticleContent'
import MermaidEnhancer from './MermaidEnhancer.vue'

const props = defineProps<{ content: PublicArticleContent }>()
const articleRoot = ref<HTMLElement | null>(null)
const copyStatus = ref('')
let statusTimer = 0

defineExpose({ rootElement: articleRoot })
useScrollProgress(articleRoot)

const formatDate = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'long',
  timeZone: 'Asia/Shanghai',
}).format(new Date(value))

function codeText(block: Element) {
  const lines = Array.from(block.querySelectorAll<HTMLElement>('pre code .line'))
  if (lines.length) return lines.map(line => line.textContent || '').join('\n')
  return block.querySelector('pre code')?.textContent || ''
}

async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.append(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()
  if (!copied) throw new Error('copy command failed')
}

function announce(message: string) {
  copyStatus.value = message
  window.clearTimeout(statusTimer)
  statusTimer = window.setTimeout(() => { copyStatus.value = '' }, 2400)
}

function handleCopyClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  const button = target.closest<HTMLButtonElement>('[data-code-copy]')
  const block = button?.closest('[data-code-block]')
  if (!button || !block) return
  const originalLabel = button.dataset.copyOriginal || button.textContent || '复制代码'
  button.dataset.copyOriginal = originalLabel
  void copyText(codeText(block)).then(() => {
    button.textContent = '已复制'
    announce('代码已复制到剪贴板。')
    window.setTimeout(() => { button.textContent = originalLabel }, 2000)
  }).catch(() => {
    button.textContent = '复制失败'
    announce('复制失败，请手动选择代码复制。')
    window.setTimeout(() => { button.textContent = originalLabel }, 2400)
  })
}

onMounted(() => articleRoot.value?.addEventListener('click', handleCopyClick))
onBeforeUnmount(() => {
  articleRoot.value?.removeEventListener('click', handleCopyClick)
  window.clearTimeout(statusTimer)
})
</script>

<template>
  <article ref="articleRoot" class="article-reading">
    <p class="instrument-label">SIGNAL / ARTICLE</p>
    <h1 id="article-title">{{ props.content.article.title }}</h1>
    <p class="article-meta"><time :datetime="props.content.article.publishedAt">{{ formatDate(props.content.article.publishedAt) }}</time></p>
    <p v-if="props.content.article.excerpt" class="article-excerpt">{{ props.content.article.excerpt }}</p>
    <p class="article-reading-status" role="status" aria-live="polite" aria-atomic="true">{{ copyStatus }}</p>
    <div class="safe-markdown">
      <div v-html="props.content.renderedHtml" />
      <MermaidEnhancer v-if="props.content.hasMermaid" />
    </div>
  </article>
</template>
