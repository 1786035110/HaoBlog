<script setup lang="ts">
import { ref } from 'vue'
import EmbeddedToolFrame from './EmbeddedToolFrame.vue'
import { datetimeLocalToTimestamp, timestampToDate, type TimestampResult } from '~/utils/timestampTool'
import { copyText, validateInput } from '~/utils/embeddedToolUi'

const timestamp = ref('')
const localDate = ref('')
const result = ref<TimestampResult>()
const reverseResult = ref('')
const error = ref('')
const status = ref('')

function convertTimestamp() {
  error.value = ''; status.value = ''; result.value = undefined
  const inputError = validateInput(timestamp.value, undefined, '时间戳输入')
  if (inputError) { error.value = inputError; return }
  try { result.value = timestampToDate(timestamp.value); status.value = `已按${result.value.unit}识别。` }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '时间戳转换失败。' }
}

function convertLocal() {
  error.value = ''; status.value = ''; reverseResult.value = ''
  try { reverseResult.value = datetimeLocalToTimestamp(localDate.value); status.value = 'datetime-local 已按浏览器本地时区转换为毫秒。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '本地日期转换失败。' }
}

async function copyResult(value: string) { try { await copyText(value); status.value = '结果已复制到剪贴板。' } catch (cause) { error.value = cause instanceof Error ? cause.message : '复制失败，请手动选择结果。' } }
function clearTool() { timestamp.value = ''; localDate.value = ''; result.value = undefined; reverseResult.value = ''; error.value = ''; status.value = '' }
</script>

<template>
  <EmbeddedToolFrame title="TIMESTAMP" description="自动识别秒/毫秒，显示浏览器本地时间与 UTC ISO；反向转换使用本地时区。" :error="error" :status="status">
    <div class="tool-fields">
      <div class="tool-grid">
        <label>秒 / 毫秒时间戳<input v-model="timestamp" inputmode="numeric" autocomplete="off" placeholder="例如：1735689600" @keyup.enter="convertTimestamp"></label>
        <label>datetime-local<input v-model="localDate" type="datetime-local" step="1" @keyup.enter="convertLocal"></label>
      </div>
      <div class="tool-actions">
        <button class="tool-action" type="button" @click="convertTimestamp">转换时间戳</button>
        <button class="tool-action" type="button" @click="convertLocal">反向转换</button>
        <button class="tool-action" type="button" @click="clearTool">清空</button>
      </div>
      <dl v-if="result" class="timestamp-result" aria-label="时间戳转换结果">
        <div><dt>识别单位</dt><dd>{{ result.unit }}</dd></div>
        <div><dt>本地时间</dt><dd>{{ result.local }}</dd></div>
        <div><dt>UTC ISO</dt><dd>{{ result.utc }}</dd></div>
        <div><dt>毫秒值</dt><dd>{{ result.milliseconds }}</dd></div>
        <button class="tool-action" type="button" @click="copyResult(`${result.local}\n${result.utc}`)">复制时间</button>
      </dl>
      <div v-if="reverseResult"><p class="tool-result-label">本地日期对应毫秒值</p><pre class="tool-output">{{ reverseResult }}</pre><button class="tool-action" type="button" @click="copyResult(reverseResult)">复制毫秒值</button></div>
    </div>
  </EmbeddedToolFrame>
</template>

<style scoped>
.timestamp-result { display: grid; gap: .55rem; margin: 0; padding: .8rem; border: 1px solid var(--color-border); background: var(--color-code-bg); font: var(--text-sm)/1.5 var(--font-mono); }
.timestamp-result div { display: grid; grid-template-columns: 7rem minmax(0, 1fr); gap: .75rem; }
.timestamp-result dt { color: var(--color-text-muted); }
.timestamp-result dd { margin: 0; overflow-wrap: anywhere; color: var(--color-text-main); }
.timestamp-result .tool-action { justify-self: start; }
.tool-result-label { margin: 0 0 .35rem; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
</style>
