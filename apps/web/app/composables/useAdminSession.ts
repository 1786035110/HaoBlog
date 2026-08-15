import type { components, paths } from '@haoblog/api-client'
import { ref } from 'vue'

type AdminSession = components['schemas']['AdminSessionResponse']
type LoginPayload = paths['/api/v1/admin/session']['post']['requestBody']['content']['application/json']
type ProblemResponse = components['schemas']['ProblemResponse']

export class AdminSessionError extends Error {
  constructor(readonly status: number, readonly problem?: ProblemResponse) {
    super(problem?.detail || 'Unable to complete the session request')
  }
}

const session = ref<AdminSession | null>(null)
const csrfToken = ref<string | null>(null)
const pending = ref(false)
const error = ref('')
const initialized = ref(false)
let restorePromise: Promise<boolean> | null = null

async function request<T>(path: string, init: RequestInit = {}) {
  const response = await fetch(path, { credentials: 'include', ...init })
  if (!response.ok) {
    let problem: ProblemResponse | undefined
    try {
      problem = await response.json() as ProblemResponse
    } catch {
      // 非 Problem JSON 响应使用通用提示，避免把服务端响应原文展示给管理员。
    }
    if (response.status === 401) {
      session.value = null
      csrfToken.value = null
      initialized.value = true
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

async function requestWithCsrfRetry<T>(path: string, init: RequestInit) {
  try {
    return await request<T>(path, init)
  } catch (cause) {
    if (!(cause instanceof AdminSessionError) || cause.status !== 403 || cause.problem?.code !== 'CSRF_INVALID') {
      throw cause
    }
    csrfToken.value = null
    const token = await fetchCsrf()
    const headers = new Headers(init.headers)
    headers.set('X-CSRF-TOKEN', token)
    return request<T>(path, { ...init, headers })
  }
}

export function useAdminSession() {
  function clear() {
    session.value = null
    csrfToken.value = null
  }

  async function write<T>(path: string, init: RequestInit = {}) {
    const token = csrfToken.value || await fetchCsrf()
    const headers = new Headers(init.headers)
    headers.set('X-CSRF-TOKEN', token)
    return requestWithCsrfRetry<T>(path, { ...init, headers })
  }

  async function restore() {
    if (restorePromise) return restorePromise
    restorePromise = (async () => {
      error.value = ''
      try {
        session.value = await request<AdminSession>('/api/v1/admin/session')
        return true
      } catch (cause) {
        if (!(cause instanceof AdminSessionError && cause.status === 401)) {
          error.value = cause instanceof Error ? cause.message : 'Unable to restore the session'
        }
        return false
      } finally {
        initialized.value = true
      }
    })()
    try {
      return await restorePromise
    } finally {
      restorePromise = null
    }
  }

  async function login(payload: LoginPayload) {
    pending.value = true
    error.value = ''
    try {
      session.value = await write<AdminSession>('/api/v1/admin/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      initialized.value = true
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
      await write<void>('/api/v1/admin/session', { method: 'DELETE' })
      clear()
      initialized.value = true
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to sign out'
      return false
    } finally {
      pending.value = false
    }
  }

  return { session, pending, error, initialized, restore, login, logout, request, write, clear }
}
