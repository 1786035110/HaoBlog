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
    expect(fetchMock.mock.calls[1]).toEqual(['/api/v1/admin/session', {
      credentials: 'include',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-1' },
      body: '{"username":"admin","password":"secret"}',
    }])
  })

  it('logs out with CSRF and clears the in-memory session', async () => {
    fetchMock
      .mockResolvedValueOnce(response({ token: 'csrf-2' }))
      .mockResolvedValueOnce(response(undefined, 204))
    const client = useAdminSession()
    client.session.value = { username: 'admin', role: 'ADMIN', authenticated: true }

    await expect(client.logout()).resolves.toBe(true)
    expect(client.session.value).toBeNull()
    expect(fetchMock.mock.calls.at(-1)).toEqual(['/api/v1/admin/session', {
      credentials: 'include',
      method: 'DELETE',
      headers: { 'X-CSRF-TOKEN': 'csrf-2' },
    }])
  })
})
