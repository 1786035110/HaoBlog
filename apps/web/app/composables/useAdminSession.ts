import type { components, paths } from '@haoblog/api-client'
import { ref } from 'vue'

type AdminSession = components['schemas']['AdminSessionResponse']
type LoginPayload = paths['/api/v1/admin/session']['post']['requestBody']['content']['application/json']
type ProblemResponse = components['schemas']['ProblemResponse']

class AdminSessionError extends Error {
  constructor(readonly status: number, readonly problem?: ProblemResponse) {
    super(problem?.detail || 'Unable to complete the session request')
  }
}

export function useAdminSession() {
  const session = ref<AdminSession | null>(null)
  const csrfToken = ref<string | null>(null)
  const pending = ref(false)
  const error = ref('')

  async function request<T>(path: string, init: RequestInit = {}) {
    const response = await fetch(path, { credentials: 'include', ...init })
    if (!response.ok) {
      let problem: ProblemResponse | undefined
      try {
        problem = await response.json() as ProblemResponse
      } catch {
        // 非 Problem JSON 响应使用通用提示，避免把服务端响应原文展示给管理员。
      }
      throw new AdminSessionError(response.status, problem)
    }
    return response.status === 204 ? undefined as T : await response.json() as T
  }

  async function fetchCsrf() {
    const response = await request<components['schemas']['CsrfTokenResponse']>('/api/v1/admin/csrf')
    csrfToken.value = response.token
    return response.token
  }

  async function restore() {
    try {
      session.value = await request<AdminSession>('/api/v1/admin/session')
    } catch (cause) {
      if (cause instanceof AdminSessionError && cause.status === 401) {
        session.value = null
        return false
      }
      error.value = cause instanceof Error ? cause.message : 'Unable to restore the session'
      return false
    }
    return true
  }

  async function login(payload: LoginPayload) {
    pending.value = true
    error.value = ''
    try {
      const token = csrfToken.value || await fetchCsrf()
      session.value = await request<AdminSession>('/api/v1/admin/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: JSON.stringify(payload),
      })
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to sign in'
      return false
    } finally {
      pending.value = false
    }
  }

  async function logout() {
    pending.value = true
    error.value = ''
    try {
      const token = csrfToken.value || await fetchCsrf()
      await request<void>('/api/v1/admin/session', {
        method: 'DELETE',
        headers: { 'X-CSRF-TOKEN': token },
      })
      session.value = null
      csrfToken.value = null
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to sign out'
      return false
    } finally {
      pending.value = false
    }
  }

  return { session, pending, error, restore, login, logout }
}
