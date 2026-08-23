export const MAX_TOOL_INPUT_BYTES = 1024 * 1024
export const MAX_REGEX_TEXT_BYTES = 100 * 1024

export function inputBytes(value: string) {
  return new TextEncoder().encode(value).byteLength
}

export function validateInput(value: string, maxBytes = MAX_TOOL_INPUT_BYTES, label = '输入') {
  const bytes = inputBytes(value)
  return bytes > maxBytes ? `${label}不能超过 ${formatBytes(maxBytes)}（当前 ${formatBytes(bytes)}）。` : ''
}

function formatBytes(bytes: number) {
  return bytes >= 1024 * 1024 ? '1 MiB' : `${Math.round(bytes / 1024)} KiB`
}

export async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.append(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()
  if (!copied) throw new Error('浏览器拒绝了复制操作，请手动选择结果。')
}
