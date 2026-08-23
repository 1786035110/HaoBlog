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

async function run(mode: 'encode' | 'decode') {
  error.value = ''; status.value = ''
  const inputError = validateInput(input.value, undefined, 'URL 输入')
  if (inputError) { error.value = inputError; return }
  busy.value = true
  try { output.value = (await worker?.run('url-codec', { input: input.value, mode }))?.output || ''; status.value = mode === 'encode' ? '文本已编码。' : '百分号编码已严格校验并解码。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'URL 编解码失败，请检查输入。' }
  finally { busy.value = false }
}

async function copyResult() { try { await copyText(output.value); status.value = '结果已复制到剪贴板。' } catch (cause) { error.value = cause instanceof Error ? cause.message : '复制失败，请手动选择结果。' } }
function clearTool() { input.value = ''; output.value = ''; error.value = ''; status.value = '' }
</script>

<template>
  <EmbeddedToolFrame title="URL CODEC" description="使用 encodeURIComponent / decodeURIComponent 处理 URI 组件。" :error="error" :status="status">
    <div class="tool-fields">
      <label>输入<textarea v-model="input" spellcheck="false" autocomplete="off" placeholder="例如：极夜观测站?mode=scan" /></label>
      <div class="tool-actions">
        <button class="tool-action" type="button" :disabled="busy" @click="run('encode')">编码</button>
        <button class="tool-action" type="button" :disabled="busy" @click="run('decode')">解码</button>
        <button class="tool-action" type="button" :disabled="!output" @click="copyResult">复制结果</button>
        <button class="tool-action" type="button" @click="clearTool">清空</button>
      </div>
      <div><p class="tool-result-label">结果</p><pre class="tool-output" aria-label="URL 结果">{{ output }}</pre></div>
    </div>
  </EmbeddedToolFrame>
</template>

<style scoped>.tool-result-label { margin: 0 0 .35rem; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }</style>
