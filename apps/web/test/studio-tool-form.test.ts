import { describe, expect, it } from 'vitest'
import { toolFormSnapshot, toolFormToCreateRequest, toolFormToUpdateRequest, toolToForm, validateToolForm } from '../app/utils/studioToolForm'

describe('Studio tool form mapping', () => {
  it('maps embedded tools without leaking URL fields and keeps version for updates', () => {
    const form = toolToForm({ id: 'tool-1', categoryId: 'cat-1', type: 'EMBEDDED', status: 'ACTIVE', title: 'JSON', slug: 'json', description: null, url: null, imageUrl: null, componentKey: 'json-format', tags: ['json', 'format'], sortOrder: 2, version: 4, createdAt: '', updatedAt: '' })
    expect(toolFormToCreateRequest(form)).toMatchObject({ categoryId: 'cat-1', componentKey: 'json-format', url: null, tags: ['json', 'format'] })
    expect(toolFormToUpdateRequest(form)).toMatchObject({ version: 4, status: 'ACTIVE' })
    expect(toolFormSnapshot(form)).not.toContain('version')
  })

  it('validates protocol and type/component field combinations', () => {
    const form = toolToForm(null, 'cat-1')
    form.title = 'JSON'; form.slug = 'json'; form.type = 'LINK'; form.url = 'http://example.com'
    expect(validateToolForm(form).url).toContain('https')
    form.type = 'EMBEDDED'; form.url = ''; form.componentKey = ''
    expect(validateToolForm(form).componentKey).toBeTruthy()
  })
})
