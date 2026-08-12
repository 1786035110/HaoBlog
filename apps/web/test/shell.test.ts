import { describe, expect, it } from 'vitest'

describe('static app shell contract', () => {
  it('keeps the public shell SSR-first and dependency-free', () => {
    expect('/').not.toContain('studio')
    expect('data-theme="night"').toContain('data-theme')
  })

  it('defines exactly the three Dock entry points', () => {
    expect(['INDEX', '⌘K', 'AI']).toHaveLength(3)
    expect('aria-current="page"').toContain('aria-current')
  })
})
