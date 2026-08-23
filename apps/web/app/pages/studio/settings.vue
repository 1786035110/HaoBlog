<template>
  <section class="settings-scene" aria-labelledby="settings-title">
    <p class="instrument-label">STUDIO / SITE CONTROL</p>
    <h1 id="settings-title">站点开关</h1>
    <p v-if="loading" class="signal-note" role="status">正在读取站点控制面板…</p>
    <p v-else-if="error" class="form-error" role="alert">{{ error }}</p>
    <form v-else-if="site" class="settings-console" @submit.prevent="save">
      <p class="console-label">GLOBAL / COMMENT SIGNAL</p>
      <label class="switch-line" for="global-comments-enabled">
        <input id="global-comments-enabled" v-model="draft" type="checkbox">
        <span>允许公开评论</span>
      </label>
      <p class="signal-note">关闭后，公共文章与评论表单会在下一次请求中反映状态；公共缓存策略最长 60 秒。</p>
      <p class="signal-note">当前版本：<span class="mono">{{ site.version }}</span></p>
      <p v-if="saved" class="success-note" role="status">站点开关已写入，公共信号已刷新。</p>
      <button class="instrument-button" type="submit" :disabled="saving || draft === site.commentsEnabled">
        {{ saving ? 'WRITING…' : '保存全局开关' }}
      </button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Site } from '../../composables/useAdminSite'

definePageMeta({ layout: 'studio' })

const { getSite, updateSite } = useAdminSite()
const site = ref<Site | null>(null)
const draft = ref(true)
const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const error = ref('')

async function load() {
  try {
    site.value = await getSite()
    draft.value = site.value.commentsEnabled
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '站点设置暂时不可用。'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!site.value || saving.value || draft.value === site.value.commentsEnabled) return
  saving.value = true
  saved.value = false
  error.value = ''
  try {
    site.value = await updateSite({ version: site.value.version, commentsEnabled: draft.value })
    saved.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '站点开关写入失败。'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.settings-scene { max-width: 48rem; margin: 10vh auto 0; }
.settings-scene h1 { margin: .8rem 0; font-size: clamp(2.5rem, 8vw, 6rem); }
.settings-console { display: grid; gap: var(--space-5); max-width: 34rem; margin-top: var(--space-8); padding: var(--space-5) 0 var(--space-5) var(--space-5); border-left: 2px solid var(--color-accent); background: linear-gradient(90deg, color-mix(in srgb, var(--color-accent) 7%, transparent), transparent 75%); }
.console-label, .switch-line { color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); letter-spacing: .1em; }
.switch-line { display: inline-flex; align-items: center; gap: .6rem; width: fit-content; cursor: pointer; }
.switch-line input { width: 1rem; height: 1rem; accent-color: var(--color-accent); }
.mono { font-family: var(--font-mono); }
.instrument-button { width: fit-content; padding: .7rem 1rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); }
.instrument-button:hover:not(:disabled), .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-accent-ink); }
.instrument-button:disabled { cursor: not-allowed; opacity: .5; }
.form-error { color: var(--color-warn); font: var(--text-sm)/1.5 var(--font-mono); }
.success-note { color: var(--color-accent); font: var(--text-sm)/1.5 var(--font-mono); }
@media (max-width: 360px) { .settings-console { padding-left: var(--space-3); } }
</style>
