export type TimestampResult = { unit: '秒' | '毫秒'; milliseconds: number; local: string; utc: string }

const MAX_DATE_MS = 8_640_000_000_000_000

export function timestampToDate(value: string): TimestampResult {
  const trimmed = value.trim()
  if (!trimmed) throw new Error('请输入秒或毫秒时间戳。')
  if (!/^[+-]?\d+$/.test(trimmed)) throw new Error('时间戳必须是整数，不要包含空格、小数或单位。')
  const number = Number(trimmed)
  if (!Number.isSafeInteger(number)) throw new Error('时间戳超出 JavaScript 可安全表示的整数范围。')
  const unit = Math.abs(number) < 100_000_000_000 ? '秒' : '毫秒'
  const milliseconds = unit === '秒' ? number * 1000 : number
  if (!Number.isSafeInteger(milliseconds) || Math.abs(milliseconds) > MAX_DATE_MS) throw new Error('时间戳超出 JavaScript Date 支持的范围。')
  const date = new Date(milliseconds)
  if (Number.isNaN(date.getTime())) throw new Error('时间戳无法转换为有效日期。')
  return { unit, milliseconds, local: date.toLocaleString('zh-CN', { hour12: false }), utc: date.toISOString() }
}

export function datetimeLocalToTimestamp(value: string) {
  if (!value) throw new Error('请选择本地日期和时间。')
  const match = /^(\d{4,6})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(value)
  if (!match) throw new Error('datetime-local 格式无效。')
  const [, year, month, day, hour, minute, second = '0', fraction = '0'] = match
  const date = new Date(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second), Number(fraction.padEnd(3, '0')))
  if (Number.isNaN(date.getTime()) || date.getFullYear() !== Number(year) || date.getMonth() !== Number(month) - 1 || date.getDate() !== Number(day)
    || date.getHours() !== Number(hour) || date.getMinutes() !== Number(minute) || date.getSeconds() !== Number(second)) {
    throw new Error('datetime-local 不是一个有效的本地日期。')
  }
  if (Math.abs(date.getTime()) > MAX_DATE_MS) throw new Error('日期超出 JavaScript Date 支持的范围。')
  return String(date.getTime())
}
