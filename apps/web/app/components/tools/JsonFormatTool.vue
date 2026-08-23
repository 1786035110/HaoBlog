<script setup lang="ts">
import { inject, ref } from 'vue'
import EmbeddedToolFrame from './EmbeddedToolFrame.vue'
import { embeddedWorkerKey } from '~/composables/useEmbeddedWorker'
import { copyText, validateInput } from '~/utils/embeddedToolUi'

const worker = inject(embeddedWorkerKey)
const input = ref('')
const output = ref('')
const error = ref('')
const status = ref('')
const busy = ref(false)

async function run(mode: 'format' | 'compact') {
  error.value = ''; status.value = ''
  const inputError = validateInput(input.value, undefined, 'JSON 输入')
  if (inputError) { error.value = inputError; return }
  busy.value = true
  try { output.value = (await worker?.run('json-format', { input: input.value, mode }))?.output || ''; status.value = mode === 'format' ? 'JSON 校验通过，已按两空格格式化。' : 'JSON 校验通过，已压缩。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'JSON 解析失败，请检查输入。' }
  finally { busy.value = false }
}

async function copyResult() {
  if (!output.value) return
  try { await copyText(output.value); status.value = '结果已复制到剪贴板。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '复制失败，请手动选择结果。' }
}

function clearTool() { input.value = ''; output.value = ''; error.value = ''; status.value = '' }
</script>

<template>
  <EmbeddedToolFrame title="JSON FORMAT" description="校验 JSON，并在浏览器中格式化或压缩。" :error="error" :status="status">
    <div class="tool-fields">
      <label>JSON 输入<textarea v-model="input" spellcheck="false" autocomplete="off" placeholder='{"signal":"ready"}' /></label>
      <div class="tool-actions">
        <button class="tool-action" type="button" :disabled="busy" @click="run('format')">格式化 / 两空格</button>
        <button class="tool-action" type="button" :disabled="busy" @click="run('compact')">压缩</button>
        <button class="tool-action" type="button" :disabled="!output" @click="copyResult">复制结果</button>
        <button class="tool-action" type="button" @click="clearTool">清空</button>
      </div>
      <div><p class="tool-result-label">结果</p><pre class="tool-output" aria-label="JSON 结果">{{ output }}</pre></div>
    </div>
  </EmbeddedToolFrame>
</template>

<style scoped>
.tool-result-label { margin: 0 0 .35rem; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }
</style>
