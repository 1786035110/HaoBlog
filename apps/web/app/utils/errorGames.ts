export type MoveDirection = 'left' | 'right' | 'up' | 'down'

export function shouldIgnoreGameKey(event: KeyboardEvent) {
  const target = event.target
  return event.defaultPrevented || event.isComposing || event.ctrlKey || event.metaKey || event.altKey
    || (target instanceof HTMLElement && (target.isContentEditable || Boolean(target.closest('input, textarea, select'))))
}

export function merge2048Line(line: number[]) {
  const values = line.filter(Boolean)
  const merged: number[] = []
  for (let index = 0; index < values.length; index += 1) {
    if (values[index] === values[index + 1]) {
      merged.push(values[index]! * 2)
      index += 1
    } else merged.push(values[index]!)
  }
  return [...merged, ...Array(Math.max(0, 4 - merged.length)).fill(0)]
}

export function move2048(board: number[], direction: MoveDirection) {
  const next = [...board]
  for (let line = 0; line < 4; line += 1) {
    const indices = Array.from({ length: 4 }, (_, offset) => {
      if (direction === 'left') return line * 4 + offset
      if (direction === 'right') return line * 4 + 3 - offset
      if (direction === 'up') return offset * 4 + line
      return (3 - offset) * 4 + line
    })
    const merged = merge2048Line(indices.map(index => board[index]!))
    indices.forEach((index, offset) => { next[index] = merged[offset]! })
  }
  return { board: next, moved: next.some((value, index) => value !== board[index]) }
}

export function add2048Tile(board: number[], random = Math.random) {
  const empty = board.flatMap((value, index) => value ? [] : [index])
  if (!empty.length) return [...board]
  const next = [...board]
  const index = empty[Math.min(empty.length - 1, Math.floor(random() * empty.length))]!
  next[index] = random() < .9 ? 2 : 4
  return next
}

export function placeMines(total: number, count: number, safeIndex: number, random = Math.random) {
  const candidates = Array.from({ length: total }, (_, index) => index).filter(index => index !== safeIndex)
  for (let index = candidates.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1))
    ;[candidates[index], candidates[swap]] = [candidates[swap]!, candidates[index]!]
  }
  return new Set(candidates.slice(0, Math.min(count, candidates.length)))
}
