<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import EmbeddedToolFrame from './EmbeddedToolFrame.vue'
import { embeddedWorkerKey } from '~/composables/useEmbeddedWorker'
import { MAX_REGEX_TEXT_BYTES, copyText, validateInput } from '~/utils/embeddedToolUi'
import type { RegexMatch } from '~/utils/embeddedToolOperations'

const worker = inject(embeddedWorkerKey)
const pattern = ref('')
const flags = ref('g')
const text = ref('')
const matches = ref<RegexMatch[]>([])
const truncated = ref(false)
const error = ref('')
const status = ref('')
const busy = ref(false)

const segments = computed(() => {
  const parts: Array<{ text: string; match: boolean; index: number }> = []
  let cursor = 0
  for (const match of matches.value) {
    if (match.index > cursor) parts.push({ text: text.value.slice(cursor, match.index), match: false, index: cursor })
    parts.push({ text: match.text, match: true, index: match.index })
    cursor = match.end
  }
  if (cursor < text.value.length) parts.push({ text: text.value.slice(cursor), match: false, index: cursor })
  return parts
})

async function run() {
  error.value = ''; status.value = ''; matches.value = []; truncated.value = false
  for (const [value, label] of [[pattern.value, '正则 pattern'], [flags.value, '正则 flags']] as const) {
    const inputError = validateInput(value, undefined, label)
    if (inputError) { error.value = inputError; return }
  }
  const textError = validateInput(text.value, MAX_REGEX_TEXT_BYTES, '测试文本')
  if (textError) { error.value = textError; return }
  busy.value = true
  try {
    const response = await worker?.run('regex-test', { pattern: pattern.value, flags: flags.value, text: text.value }, 250)
    matches.value = response?.matches || []
    truncated.value = Boolean(response?.truncated)
    status.value = truncated.value ? '已返回前 1000 个匹配，结果已截断。' : `完成：${matches.value.length} 个匹配。`
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '正则运行失败，请检查 pattern、flags 和测试文本。' }
  finally { busy.value = false }
}

async function copyMatches() {
  try { await copyText(matches.value.map((match, index) => `${index + 1}. [${match.index}, ${match.end}) ${match.text || '（零长度匹配）'}`).join('\n')); status.value = '匹配列表已复制到剪贴板。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '复制失败，请手动选择匹配列表。' }
}

function clearTool() { pattern.value = ''; flags.value = 'g'; text.value = ''; matches.value = []; truncated.value = false; error.value = ''; status.value = '' }
</script>

<template>
  <EmbeddedToolFrame title="REGEX TEST" description="在受控 Worker 中执行 JavaScript RegExp；单次运行超过 250ms 会立即终止。" :error="error" :status="status">
    <div class="tool-fields">
      <div class="tool-grid">
        <label>pattern<input v-model="pattern" spellcheck="false" autocomplete="off" placeholder="例如：signal\\d+"></label>
        <label>flags<input v-model="flags" spellcheck="false" autocomplete="off" placeholder="gim"></label>
      </div>
      <label>测试文本<textarea v-model="text" spellcheck="false" autocomplete="off" placeholder="输入最多 100 KiB 的测试文本" /></label>
      <div class="tool-actions">
        <button class="tool-action" type="button" :disabled="busy" @click="run">运行正则</button>
        <button class="tool-action" type="button" :disabled="!matches.length" @click="copyMatches">复制匹配列表</button>
        <button class="tool-action" type="button" @click="clearTool">清空</button>
      </div>
      <div>
        <p class="tool-result-label">高亮结果（文本分段）</p>
        <pre class="tool-output regex-highlight" aria-label="正则高亮结果"><template v-for="segment in segments" :key="`${segment.index}-${segment.match}`"><mark v-if="segment.match" :aria-label="segment.text ? '匹配文本' : '零长度匹配'">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></pre>
      </div>
      <ol class="match-list" aria-label="匹配列表"><li v-for="(match, index) in matches" :key="`${match.index}-${index}`"><span>#{{ index + 1 }}</span><code>[{{ match.index }}, {{ match.end }})</code><span>{{ match.text || '（零长度匹配）' }}</span></li></ol>
      <p v-if="truncated" class="tool-frame-status">匹配数量已达到 1000 上限。</p>
    </div>
  </EmbeddedToolFrame>
</template>

<style scoped>
.tool-result-label { margin: 0 0 .35rem; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
.regex-highlight { min-height: 4rem; }
.match-list { display: grid; gap: .25rem; max-height: 14rem; overflow: auto; margin: 0; padding: .7rem .7rem .7rem 2.3rem; border: 1px solid var(--color-border); color: var(--color-text-main); font: var(--text-xs)/1.45 var(--font-mono); }
.match-list li { display: grid; grid-template-columns: 3rem 7rem minmax(0, 1fr); gap: .5rem; min-width: 0; }
.match-list span:last-child { overflow-wrap: anywhere; }
.match-list span:first-child, .match-list code { color: var(--color-text-muted); }
@media (max-width: 520px) { .match-list li { grid-template-columns: 2.5rem minmax(5rem, 1fr); } .match-list span:last-child { grid-column: 2; } }
</style>
