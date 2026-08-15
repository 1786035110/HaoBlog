import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAdminSession } from '../app/composables/useAdminSession'

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: vi.fn().mockResolvedValue(body) }
}

describe('admin session client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    const client = useAdminSession()
    client.clear()
    client.initialized.value = false
    client.error.value = ''
    client.pending.value = false
  })

  it('treats an anonymous session as recoverable signed-out state', async () => {
    fetchMock.mockResolvedValue(response({ code: 'UNAUTHENTICATED', detail: 'Authentication is required' }, 401))
    const client = useAdminSession()

    await expect(client.restore()).resolves.toBe(false)
    expect(client.session.value).toBeNull()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/session', { credentials: 'include' })
  })

  it('gets CSRF before login and keeps credentials in the request only', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-1' }))
      .mockResolvedValueOnce(response({ username: 'admin', role: 'ADMIN', authenticated: true }))
    const client = useAdminSession()

    await expect(client.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)
    expect(client.session.value?.username).toBe('admin')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/admin/session')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ credentials: 'include', method: 'POST', body: '{"username":"admin","password":"secret"}' })
    expect(fetchMock.mock.calls[1][1].headers).toBeInstanceOf(Headers)
    expect(fetchMock.mock.calls[1][1].headers.get('Content-Type')).toBe('application/json')
    expect(fetchMock.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-1')
  })

  it('logs out with CSRF and clears the in-memory session', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-2' }))
      .mockResolvedValueOnce(response(undefined, 204))
    const client = useAdminSession()
    client.session.value = { username: 'admin', role: 'ADMIN', authenticated: true }

    await expect(client.logout()).resolves.toBe(true)
    expect(client.session.value).toBeNull()
    expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/admin/session')
    expect(fetchMock.mock.calls.at(-1)?.[1]).toMatchObject({ credentials: 'include', method: 'DELETE' })
    expect(fetchMock.mock.calls.at(-1)?.[1].headers.get('X-CSRF-TOKEN')).toBe('csrf-2')
  })

  it('refreshes a stale CSRF token and retries the login once', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-old' }))
      .mockResolvedValueOnce(response({ code: 'CSRF_INVALID', detail: 'Invalid CSRF token' }, 403))
      .mockResolvedValueOnce(response({ token: 'csrf-new' }))
      .mockResolvedValueOnce(response({ username: 'admin', role: 'ADMIN', authenticated: true }))
    const client = useAdminSession()

    await expect(client.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect(fetchMock.mock.calls[3][1].headers).toBeInstanceOf(Headers)
    expect(fetchMock.mock.calls[3][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-new')
  })

  it('does not retry a normal forbidden response or retry CSRF more than once', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-1' }))
      .mockResolvedValueOnce(response({ code: 'FORBIDDEN', detail: 'Access is denied' }, 403))
    const forbiddenClient = useAdminSession()
    await expect(forbiddenClient.login({ username: 'admin', password: 'secret' })).resolves.toBe(false)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    forbiddenClient.clear()

    fetchMock.mockReset()
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-old' }))
      .mockResolvedValueOnce(response({ code: 'CSRF_INVALID', detail: 'Invalid CSRF token' }, 403))
      .mockResolvedValueOnce(response({ token: 'csrf-new' }))
      .mockResolvedValueOnce(response({ code: 'CSRF_INVALID', detail: 'Invalid CSRF token' }, 403))
    const staleClient = useAdminSession()
    await expect(staleClient.login({ username: 'admin', password: 'secret' })).resolves.toBe(false)
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('clears CSRF after a session 401 so the next login fetches a new token', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-1' }))
      .mockResolvedValueOnce(response({ username: 'admin', role: 'ADMIN', authenticated: true }))
      .mockResolvedValueOnce(response({ code: 'UNAUTHENTICATED', detail: 'Authentication is required' }, 401))
      .mockResolvedValueOnce(response({ token: 'csrf-2' }))
      .mockResolvedValueOnce(response({ username: 'admin', role: 'ADMIN', authenticated: true }))
    const client = useAdminSession()

    await expect(client.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)
    await expect(client.restore()).resolves.toBe(false)
    await expect(client.login({ username: 'admin', password: 'secret' })).resolves.toBe(true)
    expect(fetchMock.mock.calls[3][0]).toBe('/api/v1/admin/csrf')
  })

  it('clears shared session state when an authenticated request expires', async () => {
    fetchMock.mockResolvedValue(response({ code: 'UNAUTHENTICATED', detail: 'Authentication is required' }, 401))
    const client = useAdminSession()
    client.session.value = { username: 'admin', role: 'ADMIN', authenticated: true }

    await expect(client.request('/api/v1/admin/articles')).rejects.toMatchObject({ status: 401 })
    expect(client.session.value).toBeNull()
    expect(client.initialized.value).toBe(true)
  })
})
