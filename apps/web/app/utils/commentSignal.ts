export type CommentSignalTextSegment = { text: string; href?: string }

const URL_PATTERN = /https:\/\/[^\s<>"'()[\]{}]+/gi
const TRAILING_PUNCTUATION = /[.,!?;:\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\uFF09\u3011]+$/

function safeHttpsUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !/[\u0000-\u001f\u007f]/.test(value) ? url.toString() : undefined
  } catch {
    return undefined
  }
}

export function splitCommentSignalText(value: string): CommentSignalTextSegment[] {
  const segments: CommentSignalTextSegment[] = []
  const pushText = (text: string) => {
    if (!text) return
    const previous = segments.at(-1)
    if (previous && !previous.href) previous.text += text
    else segments.push({ text })
  }
  let cursor = 0
  for (const match of value.matchAll(URL_PATTERN)) {
    const start = match.index ?? 0
    const raw = match[0]
    let candidate = raw
    let suffix = ''
    while (TRAILING_PUNCTUATION.test(candidate)) {
      suffix = candidate.slice(-1) + suffix
      candidate = candidate.slice(0, -1)
    }
    const href = safeHttpsUrl(candidate)
    if (!href) continue
    if (start > cursor) pushText(value.slice(cursor, start))
    segments.push({ text: candidate, href })
    pushText(suffix)
    cursor = start + raw.length
  }
  if (cursor < value.length) pushText(value.slice(cursor))
  return segments.length ? segments : [{ text: value }]
}
