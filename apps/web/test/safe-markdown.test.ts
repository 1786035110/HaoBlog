import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SafeMarkdown from '../app/components/articles/SafeMarkdown.vue'

describe('SafeMarkdown', () => {
  it('renders only the strict basic subset as Vue nodes', () => {
    const wrapper = mount(SafeMarkdown, { props: { markdown: '# Title\n\n<script>alert(1)</script>\n\n![pixel](javascript:alert(1))\n\n[docs](https://example.com)' } })
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('a').attributes('href')).toBe('https://example.com')
  })

  it('keeps dangerous protocols as text', () => {
    const wrapper = mount(SafeMarkdown, { props: { markdown: '[bad](javascript:alert(1)) [also-bad](data:text/html,boom)' } })
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.text()).toContain('javascript:alert(1)')
  })

  it('renders an unclosed code fence and unique heading ids', () => {
    const wrapper = mount(SafeMarkdown, { props: { markdown: '## Repeat\n\n## Repeat\n\n```ts\nconst answer = 42' } })
    expect(wrapper.findAll('h2').map(node => node.attributes('id'))).toEqual(['repeat', 'repeat-2'])
    expect(wrapper.find('pre').text()).toContain('const answer = 42')
  })
})
