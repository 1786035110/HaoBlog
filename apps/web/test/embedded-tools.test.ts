import { describe, expect, it, vi } from 'vitest'
import { EmbeddedWorkerClient } from '../app/composables/useEmbeddedWorker'
import { runEmbeddedOperation } from '../app/utils/embeddedToolOperations'
import { MAX_REGEX_TEXT_BYTES, MAX_TOOL_INPUT_BYTES, inputBytes, validateInput } from '../app/utils/embeddedToolUi'
import { datetimeLocalToTimestamp, timestampToDate } from '../app/utils/timestampTool'

describe('embedded tool operations', () => {
  it('formats and compacts JSON without evaluating input', () => {
    expect(runEmbeddedOperation('json-format', { input: '{"中文":[1,true]}', mode: 'format' }).output).toBe('{\n  "中文": [\n    1,\n    true\n  ]\n}')
    expect(runEmbeddedOperation('json-format', { input: '{ "a": 1 }', mode: 'compact' }).output).toBe('{"a":1}')
    expect(() => runEmbeddedOperation('json-format', { input: '{oops', mode: 'format' })).toThrow()
  })

  it('enforces byte limits, including Unicode bytes, before a task is sent', () => {
    expect(inputBytes('🙂')).toBe(4)
    expect(validateInput('a'.repeat(MAX_TOOL_INPUT_BYTES))).toBe('')
    expect(validateInput('a'.repeat(MAX_TOOL_INPUT_BYTES + 1))).toMatch(/1 MiB/)
    expect(validateInput('a'.repeat(MAX_REGEX_TEXT_BYTES + 1), MAX_REGEX_TEXT_BYTES, '测试文本')).toMatch(/100 KiB/)
    expect(runEmbeddedOperation('base64', { input: '', mode: 'encode' }).output).toBe('')
    expect(runEmbeddedOperation('url-codec', { input: '', mode: 'decode' }).output).toBe('')
    expect(() => timestampToDate('')).toThrow()
  })

  it('round-trips Chinese UTF-8 text and rejects malformed Base64', () => {
    const encoded = runEmbeddedOperation('base64', { input: '极夜观测站 / signal', mode: 'encode' }).output
    expect(encoded).toBe('5p6B5aSc6KeC5rWL56uZIC8gc2lnbmFs')
    expect(runEmbeddedOperation('base64', { input: encoded || '', mode: 'decode' }).output).toBe('极夜观测站 / signal')
    expect(() => runEmbeddedOperation('base64', { input: 'abc', mode: 'decode' })).toThrow(/Base64/)
    expect(() => runEmbeddedOperation('base64', { input: 'AB==', mode: 'decode' })).toThrow(/填充位/)
  })

  it('encodes Unicode URL components and explains invalid escapes', () => {
    const encoded = runEmbeddedOperation('url-codec', { input: '极夜观测站?x=1', mode: 'encode' }).output
    expect(encoded).toBe('%E6%9E%81%E5%A4%9C%E8%A7%82%E6%B5%8B%E7%AB%99%3Fx%3D1')
    expect(runEmbeddedOperation('url-codec', { input: encoded || '', mode: 'decode' }).output).toBe('极夜观测站?x=1')
    expect(() => runEmbeddedOperation('url-codec', { input: '%E0%A4%A', mode: 'decode' })).toThrow(/非法百分号转义/)
  })

  it('recognizes seconds and milliseconds and keeps the browser timezone in reverse conversion', () => {
    expect(timestampToDate('0')).toMatchObject({ unit: '秒', milliseconds: 0, utc: '1970-01-01T00:00:00.000Z' })
    expect(timestampToDate('1735689600000')).toMatchObject({ unit: '毫秒', milliseconds: 1735689600000, utc: '2025-01-01T00:00:00.000Z' })
    expect(() => timestampToDate('8640000000000001')).toThrow(/Date|范围/)
    expect(Number(datetimeLocalToTimestamp('1970-01-01T00:00'))).toBe(new Date(1970, 0, 1).getTime())
    expect(() => datetimeLocalToTimestamp('2025-02-31T10:00')).toThrow(/有效的本地日期/)
  })

  it('returns flags, zero-length matches and at most 1000 matches', () => {
    expect(runEmbeddedOperation('regex-test', { pattern: 'é', flags: 'giu', text: 'É é' }).matches).toHaveLength(2)
    expect(runEmbeddedOperation('regex-test', { pattern: '^', flags: 'gm', text: 'a\nb' }).matches).toEqual([
      { index: 0, end: 0, text: '' }, { index: 2, end: 2, text: '' },
    ])
    const result = runEmbeddedOperation('regex-test', { pattern: 'x', flags: 'g', text: 'x'.repeat(1001) })
    expect(result.matches).toHaveLength(1000)
    expect(result.truncated).toBe(true)
  })
})

describe('embedded worker lifecycle', () => {
  it('terminates and rebuilds a worker after a regex timeout', async () => {
    class HangingWorker {
      static instances: HangingWorker[] = []
      onmessage: ((event: MessageEvent) => void) | null = null
      onerror: (() => void) | null = null
      terminated = false
      constructor() { HangingWorker.instances.push(this) }
      postMessage() {}
      terminate() { this.terminated = true }
    }
    vi.stubGlobal('Worker', HangingWorker)
    const client = new EmbeddedWorkerClient()
    await expect(client.run('regex-test', { pattern: '(a+)+$', flags: '', text: 'a'.repeat(100) + '!' }, 10)).rejects.toThrow(/250ms/)
    expect(HangingWorker.instances[0].terminated).toBe(true)
    expect(HangingWorker.instances).toHaveLength(2)
    client.destroy()
    expect(HangingWorker.instances[1].terminated).toBe(true)
    vi.unstubAllGlobals()
  })

  it('ignores a response that arrived after its request was removed', async () => {
    class LateWorker {
      static instance: LateWorker
      onmessage: ((event: MessageEvent) => void) | null = null
      onerror: (() => void) | null = null
      constructor() { LateWorker.instance = this }
      postMessage(message: { id: number }) { setTimeout(() => this.onmessage?.({ data: { id: message.id, ok: true, result: { output: 'late' } } } as MessageEvent), 20) }
      terminate() {}
    }
    vi.stubGlobal('Worker', LateWorker)
    const client = new EmbeddedWorkerClient()
    const first = client.run('url-codec', { input: 'a', mode: 'encode' }, 5)
    await expect(first).rejects.toThrow(/250ms/)
    await new Promise(resolve => setTimeout(resolve, 30))
    client.destroy()
    vi.unstubAllGlobals()
  })
})
