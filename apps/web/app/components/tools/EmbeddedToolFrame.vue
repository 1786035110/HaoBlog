<script setup lang="ts">
defineProps<{ title: string; description: string; error?: string; status?: string }>()
</script>

<template>
  <section class="embedded-tool-frame" :aria-label="title">
    <header class="tool-frame-heading">
      <div>
        <p class="tool-frame-kicker">LOCAL / NO UPLOAD</p>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <span class="tool-frame-light" aria-hidden="true" />
    </header>
    <p v-if="error" class="tool-frame-error" role="alert">{{ error }}</p>
    <p v-if="status" class="tool-frame-status" role="status" aria-live="polite">{{ status }}</p>
    <slot />
  </section>
</template>

<style scoped>
.embedded-tool-frame { display: grid; gap: 1rem; width: 100%; padding: 1rem; border: 1px solid var(--color-border); background: var(--color-surface-quiet); }
.tool-frame-heading { display: flex; justify-content: space-between; gap: 1rem; }
.tool-frame-kicker, .tool-frame-status, .tool-frame-error, .tool-frame-heading h2 + p { margin: 0; font: var(--text-xs)/1.5 var(--font-mono); }
.tool-frame-kicker { color: var(--color-accent); letter-spacing: .1em; }
.tool-frame-heading h2 { margin: .3rem 0; color: var(--color-text-main); font: 700 clamp(1.25rem, 3vw, 1.8rem)/1.1 var(--font-display); }
.tool-frame-heading h2 + p { color: var(--color-text-muted); }
.tool-frame-light { flex: 0 0 .5rem; width: .5rem; height: .5rem; margin-top: .35rem; border-radius: 50%; background: var(--color-accent); }
.tool-frame-error { padding: .65rem .75rem; border-left: 2px solid var(--color-warn); background: color-mix(in srgb, var(--color-warn) 8%, transparent); color: var(--color-warn); }
.tool-frame-status { color: var(--color-accent); }
:deep(.tool-fields) { display: grid; gap: .75rem; }
:deep(.tool-fields label) { display: grid; gap: .35rem; color: var(--color-text-muted); font: var(--text-xs)/1.3 var(--font-mono); }
:deep(.tool-fields textarea), :deep(.tool-fields input), :deep(.tool-fields select) { width: 100%; min-width: 0; padding: .65rem .7rem; border: 1px solid var(--color-border); border-radius: 0; background: var(--color-bg-base); color: var(--color-text-main); font: var(--text-sm)/1.5 var(--font-body); }
:deep(.tool-fields textarea) { min-height: 8rem; resize: vertical; }
:deep(.tool-fields textarea:focus), :deep(.tool-fields input:focus), :deep(.tool-fields select:focus) { border-color: var(--color-accent); outline: 1px solid var(--color-accent); }
:deep(.tool-actions) { display: flex; flex-wrap: wrap; gap: .5rem; }
:deep(.tool-action) { min-height: 2.4rem; padding: .55rem .75rem; border: 1px solid var(--color-accent); background: transparent; color: var(--color-accent); cursor: pointer; font: var(--text-xs)/1 var(--font-mono); letter-spacing: .04em; }
:deep(.tool-action:hover), :deep(.tool-action:focus-visible) { background: var(--color-accent); color: var(--color-accent-ink); }
:deep(.tool-action:disabled) { cursor: wait; opacity: .55; }
:deep(.tool-output) { min-width: 0; margin: 0; padding: .8rem; overflow: auto; border: 1px solid var(--color-border); background: var(--color-code-bg); color: var(--color-text-main); font: var(--text-sm)/1.55 var(--font-mono); white-space: pre-wrap; overflow-wrap: anywhere; }
:deep(.tool-output:empty)::before { content: 'RESULT / WAITING'; color: var(--color-text-muted); }
:deep(.tool-output mark) { padding: 0 .1rem; background: color-mix(in srgb, var(--color-accent) 30%, transparent); color: var(--color-text-main); }
:deep(.tool-grid) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
@media (max-width: 520px) { .embedded-tool-frame { padding: .75rem; } .tool-frame-heading { align-items: start; } :deep(.tool-grid) { grid-template-columns: 1fr; } }
</style>
