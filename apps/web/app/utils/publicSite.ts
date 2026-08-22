import type { components } from '@haoblog/api-client'

export type PublicSite = components['schemas']['SiteResponse']

export const defaultPublicSite: PublicSite = {
  title: 'HaoBlog',
  description: '极夜观测站',
  siteUrl: 'http://localhost:3000',
  authorName: 'Hao',
}

export function usePublicSite() {
  return usePublicApi<PublicSite>('/api/v1/public/site', {
    key: 'public-site',
    default: () => defaultPublicSite,
  })
}
