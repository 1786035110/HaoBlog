export type EmbeddedWorkerTask = 'json-format' | 'base64' | 'url-codec' | 'regex-test'

export type EmbeddedWorkerPayload =
  | { input: string; mode: 'format' | 'compact' }
  | { input: string; mode: 'encode' | 'decode' }
  | { input: string; mode: 'encode' | 'decode' }
  | { pattern: string; flags: string; text: string }

export type RegexMatch = { index: number; end: number; text: string }

export type EmbeddedWorkerResult = {
  output?: string
  matches?: RegexMatch[]
  truncated?: boolean
}

const BASE64_PATTERN = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/

export function runEmbeddedOperation(task: EmbeddedWorkerTask, payload: EmbeddedWorkerPayload): EmbeddedWorkerResult {
  if (task === 'json-format') return runJson(payload as Extract<EmbeddedWorkerPayload, { mode: 'format' | 'compact' }>)
  if (task === 'base64') return runBase64(payload as Extract<EmbeddedWorkerPayload, { mode: 'encode' | 'decode' }>)
  if (task === 'url-codec') return runUrlCodec(payload as Extract<EmbeddedWorkerPayload, { mode: 'encode' | 'decode' }>)
  return runRegex(payload as Extract<EmbeddedWorkerPayload, { pattern: string }>)
}

function runJson(payload: Extract<EmbeddedWorkerPayload, { mode: 'format' | 'compact' }>) {
  if (!payload.input) throw new Error('请输入 JSON 内容。')
  const value: unknown = JSON.parse(payload.input)
  return { output: JSON.stringify(value, null, payload.mode === 'format' ? 2 : 0) }
}

function runBase64(payload: Extract<EmbeddedWorkerPayload, { mode: 'encode' | 'decode' }>) {
  if (payload.mode === 'encode') {
    const bytes = new TextEncoder().encode(payload.input)
    let binary = ''
    for (const byte of bytes) binary += String.fromCharCode(byte)
    return { output: btoa(binary) }
  }
  if (!BASE64_PATTERN.test(payload.input) || payload.input.length % 4 !== 0) {
    throw new Error('Base64 只接受规范的 A–Z、a–z、0–9、+、/ 字符和末尾 = 填充。')
  }
  const binary = atob(payload.input)
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0))
  const output = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  let canonical = ''
  for (const byte of bytes) canonical += String.fromCharCode(byte)
  if (btoa(canonical) !== payload.input) throw new Error('Base64 填充位无效，请检查编码内容。')
  return { output }
}

function runUrlCodec(payload: Extract<EmbeddedWorkerPayload, { mode: 'encode' | 'decode' }>) {
  try {
    return { output: payload.mode === 'encode' ? encodeURIComponent(payload.input) : decodeURIComponent(payload.input) }
  } catch {
    throw new Error(payload.mode === 'decode' ? 'URL 含有非法百分号转义（需要 % 后跟两位十六进制数字）。' : '文本包含无法编码的字符（可能是未配对的 Unicode 代理项）。')
  }
}

function runRegex(payload: Extract<EmbeddedWorkerPayload, { pattern: string }>) {
  let expression: RegExp
  try {
    expression = new RegExp(payload.pattern, payload.flags)
  } catch (error) {
    throw new Error(`正则表达式无效：${error instanceof Error ? error.message : 'pattern 或 flags 不合法。'}`)
  }
  const matches: RegexMatch[] = []
  const global = expression.global || expression.sticky
  do {
    const match = expression.exec(payload.text)
    if (!match) break
    matches.push({ index: match.index, end: match.index + match[0].length, text: match[0] })
    if (matches.length === 1000) return { matches, truncated: Boolean(expression.exec(payload.text)) }
    if (!global) break
    if (!match[0].length) expression.lastIndex = advanceStringIndex(payload.text, expression.lastIndex, expression.unicode)
  } while (global)
  return { matches, truncated: false }
}

function advanceStringIndex(text: string, index: number, unicode: boolean) {
  if (!unicode || index + 1 >= text.length) return index + 1
  const first = text.charCodeAt(index)
  const second = text.charCodeAt(index + 1)
  return first >= 0xd800 && first <= 0xdbff && second >= 0xdc00 && second <= 0xdfff ? index + 2 : index + 1
}
