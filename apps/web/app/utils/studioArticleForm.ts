import type { components } from '@haoblog/api-client'

type Article = components['schemas']['AdminArticleResponse']
type CreateRequest = components['schemas']['AdminArticleCreateRequest']
type UpdateRequest = components['schemas']['AdminArticleUpdateRequest']

export type ArticleFormModel = {
  title: string
  slug: string
  excerpt: string
  seoTitle: string
  seoDescription: string
  categoryId: string
  tagIds: string[]
  coverMediaId: string | null
  scheduledAt: string
  markdown: string
  version: number | null
}

export type ArticleFormErrors = Partial<Record<'title' | 'slug' | 'markdown' | 'scheduledAt', string>>

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

function localDateTime(iso: string | null | undefined) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.valueOf())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function isoDateTime(value: string) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? null : date.toISOString()
}

export function articleToForm(article?: Article | null): ArticleFormModel {
  return {
    title: article?.title || '',
    slug: article?.slug || '',
    excerpt: article?.excerpt || '',
    seoTitle: article?.seoTitle || '',
    seoDescription: article?.seoDescription || '',
    categoryId: article?.categoryId || '',
    tagIds: [...(article?.tagIds || [])],
    coverMediaId: article?.coverMediaId || null,
    scheduledAt: localDateTime(article?.scheduledAt),
    markdown: article?.markdown || '',
    version: article?.version ?? null,
  }
}

function blank(value: string) {
  return value.trim() || null
}

function commonPayload(form: ArticleFormModel) {
  return {
    slug: blank(form.slug),
    title: form.title.trim(),
    excerpt: blank(form.excerpt),
    markdown: form.markdown,
    seoTitle: blank(form.seoTitle),
    seoDescription: blank(form.seoDescription),
    scheduledAt: isoDateTime(form.scheduledAt),
    categoryId: form.categoryId || null,
    coverMediaId: form.coverMediaId,
    tagIds: [...form.tagIds],
  }
}

export function formToCreateRequest(form: ArticleFormModel): CreateRequest {
  return commonPayload(form)
}

export function formToUpdateRequest(form: ArticleFormModel): UpdateRequest {
  return { ...commonPayload(form), version: form.version ?? 0 }
}

export function validateArticleForm(form: ArticleFormModel): ArticleFormErrors {
  const errors: ArticleFormErrors = {}
  const title = form.title.trim()
  if (!title) errors.title = '标题不能为空。'
  else if (title.length > 240) errors.title = '标题不能超过 240 个字符。'
  if (form.slug.trim() && (!slugPattern.test(form.slug.trim()) || form.slug.trim().length > 160)) {
    errors.slug = 'Slug 只能使用小写字母、数字和连字符。'
  }
  if (new TextEncoder().encode(form.markdown).length > 1024 * 1024) errors.markdown = 'Markdown 不能超过 1 MiB。'
  if (form.scheduledAt && !isoDateTime(form.scheduledAt)) errors.scheduledAt = '定时时间格式无效。'
  return errors
}

export function articleFormSnapshot(form: ArticleFormModel) {
  return JSON.stringify({ ...form, tagIds: [...form.tagIds].sort() })
}

export function shouldConfirmArticleLeave(dirty: boolean, authenticated: boolean, confirm: () => boolean) {
  return !dirty || !authenticated || confirm()
}

export { isoDateTime, localDateTime }
