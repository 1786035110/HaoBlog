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
  const inputError = validateInput(input.value, undefined, 'Base64 输入')
  if (inputError) { error.value = inputError; return }
  busy.value = true
  try { output.value = (await worker?.run('base64', { input: input.value, mode }))?.output || ''; status.value = mode === 'encode' ? 'UTF-8 文本已编码。' : 'Base64 已严格校验并解码为 UTF-8 文本。' }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Base64 处理失败，请检查输入。' }
  finally { busy.value = false }
}

async function copyResult() { try { await copyText(output.value); status.value = '结果已复制到剪贴板。' } catch (cause) { error.value = cause instanceof Error ? cause.message : '复制失败，请手动选择结果。' } }
function clearTool() { input.value = ''; output.value = ''; error.value = ''; status.value = '' }
</script>

<template>
  <EmbeddedToolFrame title="BASE64" description="只处理 UTF-8 文本；本轮不读取或上传二进制文件。" :error="error" :status="status">
    <div class="tool-fields">
      <label>输入<textarea v-model="input" spellcheck="false" autocomplete="off" placeholder="输入 Unicode 文本或规范 Base64" /></label>
      <div class="tool-actions">
        <button class="tool-action" type="button" :disabled="busy" @click="run('encode')">UTF-8 编码</button>
        <button class="tool-action" type="button" :disabled="busy" @click="run('decode')">Base64 解码</button>
        <button class="tool-action" type="button" :disabled="!output" @click="copyResult">复制结果</button>
        <button class="tool-action" type="button" @click="clearTool">清空</button>
      </div>
      <div><p class="tool-result-label">结果</p><pre class="tool-output" aria-label="Base64 结果">{{ output }}</pre></div>
    </div>
  </EmbeddedToolFrame>
</template>

<style scoped>.tool-result-label { margin: 0 0 .35rem; color: var(--color-text-muted); font: var(--text-xs)/1 var(--font-mono); }</style>
