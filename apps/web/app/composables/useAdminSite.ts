import type { components } from '@haoblog/api-client'
import { useAdminSession } from './useAdminSession'

type Site = components['schemas']['AdminSiteResponse']
type SiteUpdateRequest = components['schemas']['AdminSiteUpdateRequest']

export function useAdminSite() {
  const session = useAdminSession()

  async function getSite() {
    return session.request<Site>('/api/v1/admin/site')
  }

  async function updateSite(payload: SiteUpdateRequest) {
    return session.write<Site>('/api/v1/admin/site', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  }

  return { getSite, updateSite }
}

export type { Site, SiteUpdateRequest }
