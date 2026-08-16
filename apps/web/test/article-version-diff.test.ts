import { describe, expect, it } from 'vitest'
import { diffArticleVersions } from '../app/utils/articleVersionDiff'

describe('article version line diff', () => {
  it('marks added, removed and unchanged lines with both line numbers', async () => {
    const result = await diffArticleVersions('same\nremoved\n', 'same\nadded\n')
    expect(result).toEqual([
      { kind: 'unchanged', value: 'same', oldLine: 1, newLine: 1 },
      { kind: 'removed', value: 'removed', oldLine: 2, newLine: null },
      { kind: 'added', value: 'added', oldLine: null, newLine: 2 },
    ])
  })

  it('keeps empty lines as comparable line entries', async () => {
    const result = await diffArticleVersions('top\n\nbottom', 'top\nbottom')
    expect(result?.map(line => line.kind)).toEqual(['unchanged', 'removed', 'unchanged'])
    expect(result?.[1]?.value).toBe('')
  })
})
