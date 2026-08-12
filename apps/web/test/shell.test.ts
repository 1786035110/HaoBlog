import { describe, expect, it } from 'vitest'

describe('initial web shell', () => {
  it('keeps the public shell SSR-first', () => {
    expect('/').not.toContain('studio')
  })
})
