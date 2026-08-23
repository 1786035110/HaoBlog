import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const app = resolve(import.meta.dirname, '../app')

describe('public embedded tool loading boundary', () => {
  it('keeps every tool behind the static componentKey registry and out of the closed directory route', () => {
    const page = readFileSync(resolve(app, 'pages/tools.vue'), 'utf8')
    const registry = readFileSync(resolve(app, 'utils/embeddedTools.ts'), 'utf8')
    expect(page).toContain('getEmbeddedToolComponent')
    expect(page).not.toContain('JsonFormatTool.vue')
    expect(registry).toContain("'json-format': defineAsyncComponent(() => import('~/components/tools/JsonFormatTool.vue'))")
    expect(registry).toContain("'regex-test': defineAsyncComponent(() => import('~/components/tools/RegexTestTool.vue'))")
    expect(registry).toContain('base64: defineAsyncComponent')
    expect(registry).toContain("'url-codec': defineAsyncComponent")
    expect(registry).toContain('timestamp: defineAsyncComponent')
  })

  it('uses text-node rendering and does not introduce execution or upload sinks', () => {
    const tools = readFileSync(resolve(app, 'pages/tools.vue'), 'utf8')
    const components = ['EmbeddedToolFrame.vue', 'JsonFormatTool.vue', 'Base64Tool.vue', 'UrlCodecTool.vue', 'TimestampTool.vue', 'RegexTestTool.vue']
      .map(file => readFileSync(resolve(app, 'components/tools', file), 'utf8')).join('\n')
    expect(`${tools}\n${components}`).not.toMatch(/v-html|\beval\s*\(|new Function\s*\(/)
    expect(components).toContain('aria-live="polite"')
    expect(components).toContain('<mark')
    expect(components).not.toContain('fetch(')
  })
})
