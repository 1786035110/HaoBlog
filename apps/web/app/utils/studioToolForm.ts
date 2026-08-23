import type { components } from '@haoblog/api-client'

type Tool = components['schemas']['ToolResponse']
type ToolType = components['schemas']['ToolType']
type ToolStatus = components['schemas']['ToolStatus']
type ToolComponentKey = components['schemas']['ToolComponentKey']
type CreateRequest = components['schemas']['ToolCreateRequest']
type UpdateRequest = components['schemas']['ToolUpdateRequest']

export type ToolFormModel = {
  id: string
  categoryId: string
  type: ToolType
  status: ToolStatus
  title: string
  slug: string
  description: string
  url: string
  imageUrl: string
  componentKey: ToolComponentKey | ''
  tags: string
  sortOrder: number
  version: number | null
}

export type ToolFormErrors = Partial<Record<'categoryId' | 'title' | 'slug' | 'url' | 'componentKey' | 'imageUrl' | 'tags', string>>

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const httpsPattern = /^https:\/\/[^\s]+$/i

export function toolToForm(tool?: Tool | null, categoryId = ''): ToolFormModel {
  return {
    id: tool?.id || '',
    categoryId: tool?.categoryId || categoryId,
    type: tool?.type || 'EMBEDDED',
    status: tool?.status || 'ACTIVE',
    title: tool?.title || '', slug: tool?.slug || '', description: tool?.description || '',
    url: tool?.url || '', imageUrl: tool?.imageUrl || '', componentKey: tool?.componentKey || '',
    tags: (tool?.tags || []).join(', '), sortOrder: tool?.sortOrder || 0, version: tool?.version ?? null,
  }
}

function tags(value: string) { return value.split(',').map(item => item.trim()).filter(Boolean).filter((item, index, all) => all.indexOf(item) === index) }
function nullable(value: string) { return value.trim() || null }

function common(form: ToolFormModel) {
  return {
    categoryId: form.categoryId, type: form.type, status: form.status, title: form.title.trim(), slug: form.slug.trim(),
    description: nullable(form.description), url: nullable(form.url), imageUrl: nullable(form.imageUrl),
    componentKey: form.componentKey || null, tags: tags(form.tags), sortOrder: form.sortOrder,
  }
}

export function toolFormToCreateRequest(form: ToolFormModel): CreateRequest { return common(form) }
export function toolFormToUpdateRequest(form: ToolFormModel): UpdateRequest { return { ...common(form), version: form.version ?? 0 } }

export function validateToolForm(form: ToolFormModel): ToolFormErrors {
  const errors: ToolFormErrors = {}
  if (!form.categoryId) errors.categoryId = '请选择工具分类。'
  if (!form.title.trim()) errors.title = '工具标题不能为空。'
  else if (form.title.trim().length > 160) errors.title = '工具标题不能超过 160 个字符。'
  if (!slugPattern.test(form.slug.trim()) || form.slug.trim().length > 160) errors.slug = 'Slug 只能使用小写字母、数字和连字符。'
  if (form.imageUrl.trim() && !httpsPattern.test(form.imageUrl.trim())) errors.imageUrl = '图片地址必须使用 https。'
  if (form.type === 'EMBEDDED') {
    if (!form.componentKey) errors.componentKey = '内嵌工具必须选择白名单组件。'
    if (form.url.trim()) errors.url = '内嵌工具不能填写外部 URL。'
  } else if (!httpsPattern.test(form.url.trim())) errors.url = '链接和展示工具必须填写 https URL。'
  if (tags(form.tags).length > 32) errors.tags = '标签不能超过 32 个。'
  return errors
}

export function toolFormSnapshot(form: ToolFormModel) {
  const { id: _id, version: _version, ...content } = form
  return JSON.stringify({ ...content, tags: tags(content.tags).sort() })
}

export type { ToolComponentKey, ToolStatus, ToolType }
