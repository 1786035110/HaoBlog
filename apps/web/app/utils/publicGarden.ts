import type { components } from '@haoblog/api-client'

export type GardenGraph = components['schemas']['GardenGraphResponse']
export type GardenNode = components['schemas']['GardenNode']
export type GardenEdge = components['schemas']['GardenEdge']

export const emptyGardenGraph: GardenGraph = { nodes: [], edges: [], truncated: false }

export function gardenNodeTypeLabel(type: GardenNode['type']) {
  return { ARTICLE: 'ARTICLE / 文章', TAG: 'TAG / 标签', CATEGORY: 'CATEGORY / 分类', TOOL: 'TOOL / 工具' }[type]
}

export function gardenNodeDate(node: GardenNode) {
  if (!node.publishedAt) return node.type === 'TOOL' ? 'ACTIVE / 当前工具' : '未记录日期'
  return new Date(node.publishedAt).toISOString().slice(0, 10)
}

export function sortedGardenTimeline(nodes: GardenNode[]) {
  return [...nodes].filter(node => node.type === 'ARTICLE' || node.type === 'TOOL').sort((left, right) => {
    if (left.type !== right.type) return left.type === 'ARTICLE' ? -1 : 1
    if (left.publishedAt && right.publishedAt) {
      const date = right.publishedAt.localeCompare(left.publishedAt)
      if (date) return date
    } else if (left.publishedAt) return -1
    else if (right.publishedAt) return 1
    return compareText(left.label, right.label) || compareText(left.id, right.id)
  })
}

function compareText(left: string, right: string) {
  return left < right ? -1 : left > right ? 1 : 0
}
