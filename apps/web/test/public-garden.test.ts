import { describe, expect, it } from 'vitest'
import { sortedGardenTimeline, type GardenNode } from '../app/utils/publicGarden'

describe('public garden timeline', () => {
  it('uses the same code-point ordering in SSR and the browser', () => {
    const nodes = [
      { id: 'tool:timestamp', type: 'TOOL', label: '时间戳转换', href: '/tools?tool=timestamp', degree: 0 },
      { id: 'tool:base64', type: 'TOOL', label: 'Base64 编解码', href: '/tools?tool=base64', degree: 0 },
    ] as GardenNode[]

    expect(sortedGardenTimeline(nodes).map(node => node.id)).toEqual(['tool:base64', 'tool:timestamp'])
  })
})
