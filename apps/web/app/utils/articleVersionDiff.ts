import { diffLines } from 'diff'

export type VersionDiffLine = {
  kind: 'added' | 'removed' | 'unchanged'
  value: string
  oldLine: number | null
  newLine: number | null
}

const MAX_RENDERED_LINES = 20_000

function lines(value: string) {
  const result = value.split('\n')
  if (result.at(-1) === '') result.pop()
  return result
}

export function diffArticleVersions(oldMarkdown: string, newMarkdown: string): Promise<VersionDiffLine[] | null> {
  return new Promise(resolve => {
    diffLines(oldMarkdown, newMarkdown, {
      oneChangePerToken: true,
      stripTrailingCr: true,
      timeout: 1500,
      maxEditLength: 10_000,
      callback: changes => {
        if (!changes) {
          resolve(null)
          return
        }
        const result: VersionDiffLine[] = []
        let oldLine = 1
        let newLine = 1
        for (const change of changes) {
          const kind = change.added ? 'added' : change.removed ? 'removed' : 'unchanged'
          for (const value of lines(change.value)) {
            if (result.length >= MAX_RENDERED_LINES) {
              resolve(null)
              return
            }
            result.push({
              kind,
              value,
              oldLine: kind === 'added' ? null : oldLine++,
              newLine: kind === 'removed' ? null : newLine++,
            })
          }
        }
        resolve(result)
      },
    })
  })
}
