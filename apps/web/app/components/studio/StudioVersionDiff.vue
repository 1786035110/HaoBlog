<template>
  <section class="version-diff" aria-labelledby="version-diff-title">
    <div class="diff-heading">
      <div>
        <p class="instrument-label">VERSION / LINE SIGNAL</p>
        <h2 id="version-diff-title">{{ left.sourceArticleVersion }} → {{ right.sourceArticleVersion }}</h2>
      </div>
      <p class="diff-summary" aria-live="polite">
        <span class="legend-item" data-kind="added">+ 新增</span>
        <span class="legend-item" data-kind="removed">− 删除</span>
        <span class="legend-item" data-kind="unchanged">· 未变</span>
      </p>
    </div>

    <p v-if="pending" class="signal-note" role="status">正在计算行信号…</p>
    <p v-else-if="lines === null" class="diff-limit" role="alert">
      正文差异超过浏览器安全计算上限。请缩小版本范围后重试；历史正文仍可分别打开查看。
    </p>
    <ol v-else class="diff-lines" aria-label="Markdown 行级差异">
      <li v-for="(line, index) in lines" :key="`${index}-${line.kind}-${line.oldLine}-${line.newLine}`" :data-kind="line.kind">
        <span class="line-kind" aria-hidden="true">{{ line.kind === 'added' ? '+' : line.kind === 'removed' ? '−' : '·' }}</span>
        <span class="line-number">{{ line.oldLine ?? '' }}</span>
        <span class="line-number">{{ line.newLine ?? '' }}</span>
        <code><span class="sr-only">{{ lineLabel(line.kind) }}：</span>{{ line.value || ' ' }}</code>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { components } from '@haoblog/api-client'
import { diffArticleVersions, type VersionDiffLine } from '../../utils/articleVersionDiff'

type Version = components['schemas']['AdminArticleVersionResponse']

const props = defineProps<{ left: Version; right: Version }>()
const lines = ref<VersionDiffLine[] | null>([])
const pending = ref(false)
let requestId = 0

watch(() => [props.left.id, props.right.id], async () => {
  const currentRequest = ++requestId
  pending.value = true
  lines.value = []
  const result = await diffArticleVersions(props.left.markdown, props.right.markdown)
  if (currentRequest !== requestId) return
  lines.value = result
  pending.value = false
}, { immediate: true })

function lineLabel(kind: VersionDiffLine['kind']) {
  return kind === 'added' ? '新增' : kind === 'removed' ? '删除' : '未变'
}
</script>

<style scoped>
.version-diff { min-width: 0; border-top: 1px solid var(--color-border); }
.diff-heading { display: flex; align-items: end; justify-content: space-between; gap: var(--space-4); padding: var(--space-6) 0 var(--space-3); }
.diff-heading h2 { margin: .6rem 0 0; color: var(--color-text-main); font: var(--text-lg)/1.2 var(--font-mono); }
.diff-summary { display: flex; flex-wrap: wrap; gap: var(--space-3); margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
.legend-item[data-kind='added'] { color: var(--color-accent); }
.legend-item[data-kind='removed'] { color: var(--color-warn); }
.signal-note, .diff-limit { color: var(--color-text-muted); font: var(--text-sm)/1.5 var(--font-mono); }
.diff-limit { padding: var(--space-4); border-left: 2px solid var(--color-warn); color: var(--color-warn); }
.diff-lines { overflow: auto; margin: 0; padding: 0; border: 1px solid var(--color-border); background: var(--color-code-bg); color: var(--color-text-main); list-style: none; font: var(--text-xs)/1.65 var(--font-mono); }
.diff-lines li { display: grid; grid-template-columns: 1.5rem 4rem 4rem minmax(32rem, 1fr); min-width: 42rem; border-bottom: 1px solid color-mix(in srgb, var(--color-border) 55%, transparent); }
.diff-lines li:last-child { border-bottom: 0; }
.diff-lines li[data-kind='added'] { background: color-mix(in srgb, var(--color-accent) 12%, transparent); }
.diff-lines li[data-kind='removed'] { background: color-mix(in srgb, var(--color-warn) 12%, transparent); }
.line-kind, .line-number { padding: .2rem .5rem; color: var(--color-text-muted); text-align: right; user-select: none; }
.line-kind { color: var(--color-accent); }
.diff-lines li[data-kind='removed'] .line-kind { color: var(--color-warn); }
.diff-lines code { min-width: 0; padding: .2rem .75rem; white-space: pre-wrap; overflow-wrap: anywhere; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 640px) { .diff-heading { align-items: start; flex-direction: column; } }
</style>
