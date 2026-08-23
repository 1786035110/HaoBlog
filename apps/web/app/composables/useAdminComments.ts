import type { components } from '@haoblog/api-client'
import { useAdminSession } from './useAdminSession'

type CommentList = components['schemas']['AdminCommentListResponse']
type CommentDetail = components['schemas']['AdminCommentDetail']
type ModerationRequest = components['schemas']['CommentModerationRequest']

export function useAdminComments() {
  const session = useAdminSession()

  async function listComments(params: {
    page?: number
    size?: number
    status?: components['schemas']['CommentStatus']
    articleId?: string
    keyword?: string
    direction?: 'asc' | 'desc'
  } = {}) {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
      direction: params.direction ?? 'desc',
    })
    if (params.status) query.set('status', params.status)
    if (params.articleId) query.set('articleId', params.articleId)
    if (params.keyword?.trim()) query.set('keyword', params.keyword.trim())
    return session.request<CommentList>(`/api/v1/admin/comments?${query}`)
  }

  async function getComment(id: string) {
    return session.request<CommentDetail>(`/api/v1/admin/comments/${encodeURIComponent(id)}`)
  }

  async function moderateComment(id: string, payload: ModerationRequest) {
    return session.write<CommentDetail>(`/api/v1/admin/comments/${encodeURIComponent(id)}/moderation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  }

  return { listComments, getComment, moderateComment }
}

export type { CommentDetail, CommentList, ModerationRequest }
