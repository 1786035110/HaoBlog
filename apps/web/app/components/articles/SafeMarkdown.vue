<script setup lang="ts">
import { computed, h, type VNode } from 'vue'

const props = defineProps<{ markdown: string }>()

type Block = { kind: 'heading' | 'paragraph' | 'list' | 'quote' | 'code'; level?: number; text?: string; items?: string[]; language?: string }

function parseBlocks(source: string): Block[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: Block[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index]!
    if (!line.trim()) { index++; continue }
    const fence = line.match(/^```([a-zA-Z0-9_-]*)\s*$/)
    if (fence) {
      const code: string[] = []
      index++
      while (index < lines.length && !/^```\s*$/.test(lines[index]!)) code.push(lines[index++]!)
      if (index < lines.length) index++
      blocks.push({ kind: 'code', text: code.join('\n'), language: fence[1] || undefined })
      continue
    }
    const heading = line.match(/^(#{1,3})\s+(.+?)\s*#*$/)
    if (heading) { blocks.push({ kind: 'heading', level: heading[1]!.length, text: heading[2] ?? '' }); index++; continue }
    if (/^\s*>\s?/.test(line)) {
      const quote: string[] = []
      while (index < lines.length && /^\s*>\s?/.test(lines[index]!)) quote.push(lines[index++]!.replace(/^\s*>\s?/, ''))
      blocks.push({ kind: 'quote', text: quote.join('\n') }); continue
    }
    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = []
      while (index < lines.length && /^\s*[-*+]\s+/.test(lines[index]!)) items.push(lines[index++]!.replace(/^\s*[-*+]\s+/, ''))
      blocks.push({ kind: 'list', items }); continue
    }
    const paragraph: string[] = [line]
    index++
    while (index < lines.length && lines[index]!.trim() && !/^```/.test(lines[index]!) && !/^(#{1,3})\s+/.test(lines[index]!) && !/^\s*[-*+]\s+/.test(lines[index]!) && !/^\s*>\s?/.test(lines[index]!)) paragraph.push(lines[index++]!)
    blocks.push({ kind: 'paragraph', text: paragraph.join('\n') })
  }
  return blocks
}

function slugify(value: string, seen: Map<string, number>) {
  const base = value.toLowerCase().trim().replace(/[^\p{Letter}\p{Number}]+/gu, '-').replace(/^-|-$/g, '') || 'section'
  const count = (seen.get(base) || 0) + 1
  seen.set(base, count)
  return count === 1 ? base : `${base}-${count}`
}

function inlineNodes(value: string): VNode[] {
  const nodes: VNode[] = []
  const pattern = /(!?)\[([^\]]+)\]\(([^\s)]+)\)/g
  let cursor = 0
  for (const match of value.matchAll(pattern)) {
    const start = match.index ?? 0
    if (start > cursor) nodes.push(h('span', value.slice(cursor, start)))
    const image = match[1] === '!'
    const label = match[2] ?? ''
    const href = match[3] ?? ''
    if (image && /^https:\/\//i.test(href)) nodes.push(h('img', {
      src: href,
      alt: label,
      width: 1200,
      height: 630,
      loading: 'lazy',
      decoding: 'async',
    }))
    else if (!image && /^(https?:\/\/|mailto:)/i.test(href)) nodes.push(h('a', { href, rel: 'noopener noreferrer', target: '_blank' }, label))
    else nodes.push(h('span', `${image ? '!' : ''}[${label}](${href})`))
    cursor = start + match[0].length
  }
  if (cursor < value.length) nodes.push(h('span', value.slice(cursor)))
  return nodes.length ? nodes : [h('span', value)]
}

function renderBlock(block: Block, seen: Map<string, number>) {
  if (block.kind === 'heading') {
    const tag = `h${block.level}` as 'h1' | 'h2' | 'h3'
    return h(tag, { id: slugify(block.text || '', seen) }, inlineNodes(block.text || ''))
  }
  if (block.kind === 'code') return h('pre', { class: 'safe-markdown-code' }, [h('code', block.text || '')])
  if (block.kind === 'list') return h('ul', (block.items || []).map(item => h('li', inlineNodes(item))))
  if (block.kind === 'quote') return h('blockquote', inlineNodes(block.text || ''))
  return h('p', inlineNodes(block.text || ''))
}

const rendered = computed(() => {
  const seen = new Map<string, number>()
  return parseBlocks(props.markdown).map(block => renderBlock(block, seen))
})
</script>

<template>
  <div class="safe-markdown"> <component :is="node" v-for="(node, index) in rendered" :key="index" /> </div>
</template>
