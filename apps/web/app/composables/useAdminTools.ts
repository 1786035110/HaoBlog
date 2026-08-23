import type { components } from '@haoblog/api-client'
import { useAdminSession } from './useAdminSession'

type ToolCategory = components['schemas']['ToolCategoryResponse']
type ToolCategoryCreateRequest = components['schemas']['ToolCategoryCreateRequest']
type ToolCategoryUpdateRequest = components['schemas']['ToolCategoryUpdateRequest']
type Tool = components['schemas']['ToolResponse']
type ToolCreateRequest = components['schemas']['ToolCreateRequest']
type ToolUpdateRequest = components['schemas']['ToolUpdateRequest']
type ToolList = components['schemas']['AdminToolListResponse']

export function useAdminTools() {
  const session = useAdminSession()

  async function listCategories() { return session.request<ToolCategory[]>('/api/v1/admin/tool-categories') }
  async function createCategory(payload: ToolCategoryCreateRequest) {
    return session.write<ToolCategory>('/api/v1/admin/tool-categories', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  }
  async function updateCategory(id: string, payload: ToolCategoryUpdateRequest) {
    return session.write<ToolCategory>(`/api/v1/admin/tool-categories/${encodeURIComponent(id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  }
  async function deleteCategory(id: string, version: number) {
    return session.write<void>(`/api/v1/admin/tool-categories/${encodeURIComponent(id)}?version=${version}`, { method: 'DELETE' })
  }
  async function listTools(params: {
    page?: number; size?: number; categoryId?: string; type?: components['schemas']['ToolType']; status?: components['schemas']['ToolStatus']; keyword?: string; sort?: 'sortOrder' | 'createdAt' | 'updatedAt' | 'title'; direction?: 'asc' | 'desc'
  } = {}) {
    const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20), sort: params.sort ?? 'sortOrder', direction: params.direction ?? 'asc' })
    if (params.categoryId) query.set('categoryId', params.categoryId)
    if (params.type) query.set('type', params.type)
    if (params.status) query.set('status', params.status)
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim())
    return session.request<ToolList>(`/api/v1/admin/tools?${query}`)
  }
  async function getTool(id: string) { return session.request<Tool>(`/api/v1/admin/tools/${encodeURIComponent(id)}`) }
  async function createTool(payload: ToolCreateRequest) {
    return session.write<Tool>('/api/v1/admin/tools', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  }
  async function updateTool(id: string, payload: ToolUpdateRequest) {
    return session.write<Tool>(`/api/v1/admin/tools/${encodeURIComponent(id)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  }
  async function deleteTool(id: string, version: number) {
    return session.write<void>(`/api/v1/admin/tools/${encodeURIComponent(id)}?version=${version}`, { method: 'DELETE' })
  }

  return { listCategories, createCategory, updateCategory, deleteCategory, listTools, getTool, createTool, updateTool, deleteTool }
}

export type { Tool, ToolCategory, ToolCategoryCreateRequest, ToolCategoryUpdateRequest, ToolCreateRequest, ToolList, ToolUpdateRequest }
