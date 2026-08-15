import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(import.meta.dirname, '../app')

describe('Studio editor route boundary', () => {
  it('keeps the editor behind dynamic imports on both edit routes', () => {
    for (const file of ['pages/studio/articles/new.vue', 'pages/studio/articles/[id].vue']) {
      expect(readFileSync(resolve(root, file), 'utf8')).toContain('defineAsyncComponent')
      expect(readFileSync(resolve(root, file), 'utf8')).toContain('StudioArticleEditor.client.vue')
    }
  })

  it('does not import the Studio editor from public article routes', () => {
    const publicArticle = readFileSync(resolve(root, 'pages/articles/[slug].vue'), 'utf8')
    expect(publicArticle).not.toContain('StudioArticleEditor')
  })
})
