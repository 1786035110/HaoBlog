import { describe, expect, it } from 'vitest'
import { merge2048Line, move2048, placeMines } from '../app/utils/errorGames'

describe('404 games', () => {
  it('merges each 2048 pair only once and preserves direction', () => {
    expect(merge2048Line([2, 2, 2, 2])).toEqual([4, 4, 0, 0])
    expect(move2048([2, 2, 0, 0, ...Array(12).fill(0)], 'right').board.slice(0, 4)).toEqual([0, 0, 0, 4])
  })

  it('keeps the first minesweeper cell safe', () => {
    const mines = placeMines(81, 10, 40, () => .5)
    expect(mines.size).toBe(10)
    expect(mines.has(40)).toBe(false)
  })
})
