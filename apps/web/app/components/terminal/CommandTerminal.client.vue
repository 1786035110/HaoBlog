<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import { parseTerminalCommand, terminalHelp, type TerminalCommand } from '../../utils/terminalCommands'
import { publicSearchPageUrl } from '../../utils/publicSearch'
import { useTheme } from '../../utils/theme'

type ArticleList = components['schemas']['ArticleListResponse']
type ToolList = components['schemas']['PublicToolListResponse']
type OutputLine = { id: number; text: string; href?: string }

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const value = ref('')
const outputs = ref<OutputLine[]>([])
const history = ref<string[]>([])
const historyIndex = ref(-1)
const status = ref('终端已就绪。')
const { setTheme } = useTheme()
let nextOutputId = 0

watch(() => props.open, async open => {
  await nextTick()
  if (!dialog.value) return
  if (open && !dialog.value.open) dialog.value.showModal()
  if (open) input.value?.focus()
  if (!open && dialog.value.open) dialog.value.close()
})

onMounted(async () => {
  if (props.open) {
    await nextTick()
    dialog.value?.showModal()
    input.value?.focus()
  }
})

function close() {
  if (dialog.value?.open) dialog.value.close()
  emit('close')
}

function append(text: string, href?: string) {
  outputs.value.push({ id: ++nextOutputId, text, href })
  if (outputs.value.length > 100) outputs.value = outputs.value.slice(-100)
}

function clearOutput() {
  outputs.value = []
  status.value = '输出已清空。'
}

function cycleHistory(direction: 1 | -1) {
  if (!history.value.length) return
  const next = historyIndex.value + direction
  if (next < -1 || next >= history.value.length) return
  historyIndex.value = next
  value.value = next === -1 ? '' : (history.value[history.value.length - 1 - next] || '')
}

function focusables() {
  return [...(dialog.value?.querySelectorAll<HTMLElement>('button, input, [tabindex]:not([tabindex="-1"])') || [])]
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'l') {
    event.preventDefault()
    value.value = ''
    clearOutput()
    input.value?.focus()
    return
  }
  if (event.key === 'ArrowUp' && document.activeElement === input.value) {
    event.preventDefault()
    cycleHistory(1)
    return
  }
  if (event.key === 'ArrowDown' && document.activeElement === input.value) {
    event.preventDefault()
    cycleHistory(-1)
    return
  }
  if (event.key !== 'Tab') return
  const items = focusables()
  if (items.length < 2) return
  const current = items.indexOf(document.activeElement as HTMLElement)
  const next = event.shiftKey ? (current <= 0 ? items.length - 1 : current - 1) : (current + 1) % items.length
  event.preventDefault()
  items[next]?.focus()
}

async function run(command: TerminalCommand) {
  if (command.kind === 'clear') {
    clearOutput()
    return
  }
  if (command.kind === 'help') {
    terminalHelp.forEach(line => append(line))
    status.value = '已显示帮助。'
    return
  }
  if (command.kind === 'theme') {
    setTheme(command.theme)
    append(`主题已切换为 ${command.theme === 'blueprint' ? 'blueprint / 蓝图' : 'night / 极夜'}。`)
    status.value = '主题状态已更新。'
    return
  }
  if (command.kind === 'open') {
    append(`OPEN ${command.href}`)
    status.value = '正在打开站内路径。'
    await navigateTo(command.href)
    close()
    return
  }
  if (command.kind === 'ask') {
    append('AI 信号尚未开放。本阶段不会调用 AI。')
    append('站内搜索入口：', command.question.length >= 2 ? publicSearchPageUrl(command.question, 1) : '/search')
    status.value = '已切换到安全降级。'
    return
  }

  if (command.kind === 'list') {
    status.value = `正在读取 ${command.target === 'posts' ? '公开文章' : '公开工具'}…`
    try {
      if (command.target === 'posts') {
        const result = await $fetch<ArticleList>('/api/v1/public/articles', { query: { page: 0, size: 20 } })
        if (!result.items.length) append('没有可显示的公开文章。')
        result.items.slice(0, 20).forEach(article => append(`${article.title}  /articles/${article.slug}`, `/articles/${article.slug}`))
      } else {
        const result = await $fetch<ToolList>('/api/v1/public/tools')
        if (!result.items.length) append('没有可显示的公开工具。')
        result.items.slice(0, 20).forEach(tool => append(`${tool.title}  /tools?tool=${encodeURIComponent(tool.slug)}`, `/tools?tool=${encodeURIComponent(tool.slug)}`))
      }
      status.value = '读取完成，最多显示 20 条公开结果。'
    } catch {
      append('公开信号暂时不可用，请稍后重试。')
      status.value = '读取失败。'
    }
    return
  }

  if (command.kind === 'grep') {
    status.value = '正在检索公开文章…'
    try {
      const result = await $fetch<ArticleList>('/api/v1/public/search/articles', { query: { q: command.query, page: 0, size: 20 } })
      if (!result.items.length) append('没有命中公开文章。')
      result.items.slice(0, 20).forEach(article => append(`${article.title}  /articles/${article.slug}`, `/articles/${article.slug}`))
      status.value = '检索完成，最多显示 20 条公开结果。'
    } catch {
      append('公开搜索信号暂时不可用，请稍后重试。')
      status.value = '检索失败。'
    }
  }
}

async function submit() {
  const commandText = value.value.trim()
  if (!commandText) return
  value.value = ''
  history.value = [...history.value, commandText].slice(-50)
  historyIndex.value = -1
  append(`> ${commandText}`)
  const parsed = parseTerminalCommand(commandText)
  if (parsed.kind === 'error') {
    append(`ERROR: ${parsed.message}`)
    status.value = '命令未执行。'
    return
  }
  await run(parsed)
  input.value?.focus()
}
</script>

<template>
  <dialog
    ref="dialog"
    class="command-terminal"
    role="dialog"
    aria-modal="true"
    aria-labelledby="command-terminal-title"
    aria-describedby="command-terminal-description"
    @cancel.prevent="close"
    @click.self="close"
    @keydown="handleKeydown"
  >
    <div class="command-terminal-frame">
      <header class="command-terminal-header">
        <div>
          <p class="instrument-label">COMMAND TERMINAL / LOCAL ONLY</p>
          <h2 id="command-terminal-title">安全信号终端</h2>
          <p id="command-terminal-description">仅执行浏览器内白名单命令，不连接 Shell、JavaScript 或任意网络地址。</p>
        </div>
        <button class="terminal-close" type="button" aria-label="关闭终端" @click="close">ESC ×</button>
      </header>
      <div class="command-terminal-output" role="log" aria-live="polite" aria-relevant="additions text">
        <p v-if="!outputs.length" class="terminal-empty">输入 <code>help</code> 开始。历史只保留在当前会话，最多 50 条。</p>
        <p v-for="line in outputs" :key="line.id" class="terminal-line">
          <span>{{ line.text }}</span>
          <a v-if="line.href" :href="line.href">打开 →</a>
        </p>
      </div>
      <form class="command-terminal-form" @submit.prevent="submit">
        <label for="command-terminal-input">命令</label>
        <span aria-hidden="true">$</span>
        <input id="command-terminal-input" ref="input" v-model="value" type="text" maxlength="200" autocomplete="off" spellcheck="false">
        <button type="submit">执行</button>
      </form>
      <p class="command-terminal-status" role="status">{{ status }}</p>
    </div>
  </dialog>
</template>

<style scoped>
.command-terminal { width: min(54rem, calc(100vw - 2rem)); max-height: min(44rem, calc(100vh - 5rem)); padding: 0; border: 1px solid var(--color-accent); color: var(--color-text-main); background: var(--color-bg-base); }
.command-terminal::backdrop { background: rgba(0, 0, 0, .72); }
.command-terminal-frame { display: grid; gap: 1rem; padding: clamp(1rem, 3vw, 2rem); }
.command-terminal-header { display: flex; align-items: start; justify-content: space-between; gap: 1rem; }
.command-terminal h2 { margin: .5rem 0; font-family: var(--font-display); font-size: clamp(1.8rem, 5vw, 3rem); }
.command-terminal-header p:last-child { max-width: 38rem; margin: 0; color: var(--color-text-muted); font-size: .9rem; line-height: 1.6; }
.terminal-close, .command-terminal-form button { border: 1px solid var(--color-accent); color: var(--color-accent); background: transparent; font: var(--text-xs)/1.3 var(--font-mono); cursor: pointer; }
.terminal-close { padding: .45rem .6rem; white-space: nowrap; }
.terminal-close:hover, .terminal-close:focus-visible, .command-terminal-form button:hover, .command-terminal-form button:focus-visible { color: var(--color-accent-ink); background: var(--color-accent); }
.command-terminal-output { min-height: 12rem; max-height: 22rem; overflow: auto; padding: .8rem; border-block: 1px solid var(--color-border); background: var(--color-code-bg); font: .82rem/1.65 var(--font-mono); }
.terminal-empty, .terminal-line { margin: 0; }
.terminal-empty { color: var(--color-text-muted); }
.terminal-line { display: flex; gap: .7rem; justify-content: space-between; white-space: pre-wrap; }
.terminal-line a { flex: 0 0 auto; color: var(--color-accent); }
.command-terminal-form { display: grid; grid-template-columns: auto auto minmax(0, 1fr) auto; gap: .55rem; align-items: center; color: var(--color-accent); font: var(--text-xs)/1.4 var(--font-mono); }
.command-terminal-form label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.command-terminal-form input { min-width: 0; padding: .65rem 0; border: 0; border-bottom: 1px solid var(--color-border); outline: 0; color: var(--color-text-main); background: transparent; font: inherit; }
.command-terminal-form input:focus { border-color: var(--color-accent); }
.command-terminal-form button { padding: .6rem .8rem; }
.command-terminal-status { min-height: 1.4em; margin: 0; color: var(--color-text-muted); font: var(--text-xs)/1.4 var(--font-mono); }
@media (max-width: 520px) { .command-terminal { width: calc(100vw - 1rem); max-height: calc(100vh - 1rem); } .command-terminal-frame { padding: .8rem; } .command-terminal-output { min-height: 10rem; } .terminal-line { display: grid; gap: .15rem; } }
@media (prefers-reduced-motion: reduce) { .command-terminal *, .command-terminal *::before, .command-terminal *::after { transition: none !important; animation: none !important; } }
</style>
