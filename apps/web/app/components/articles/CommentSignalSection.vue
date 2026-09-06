<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { components } from '@haoblog/api-client'
import CommentSignalItem from './CommentSignalItem.vue'

type CommentPage = components['schemas']['CommentPageResponse']
type CommentView = components['schemas']['CommentView']
type CommentFormContext = components['schemas']['CommentFormContext']
type Submission = components['schemas']['CommentSubmissionResponse']
type FormStatus = 'idle' | 'loading' | 'submitting' | 'published' | 'rate-limited' | 'error'

const emit = defineEmits<{ published: [] }>()

const props = defineProps<{
  slug: string
  comments: CommentPage
  unavailable?: boolean
  siteCommentsEnabled: boolean
  articleCommentsEnabled: boolean
}>()

const expanded = ref(false)
const formContext = ref<CommentFormContext | null>(null)
const formStatus = ref<FormStatus>('idle')
const formMessage = ref('')
const actionMessage = ref('')
const retryAfter = ref<number | null>(null)
const replyTo = ref<CommentView | null>(null)
const contentInput = ref<HTMLTextAreaElement | null>(null)
const ownedTokens = ref<Record<string, string>>({})
const hiddenIds = ref(new Set<string>())
const formReadyAt = ref(0)
const form = reactive({ nickname: '', email: '', content: '', website: '' })

const isGloballyClosed = computed(() => !props.siteCommentsEnabled)
const isArticleClosed = computed(() => props.siteCommentsEnabled && !props.articleCommentsEnabled)
const canComment = computed(() => !isGloballyClosed.value && !isArticleClosed.value)
const visibleComments = computed(() => props.comments.items.filter(comment => !hiddenIds.value.has(comment.id)))
const contentLength = computed(() => [...form.content].length)

function tokenKey(id: string) { return `haoblog-comment-delete:${id}` }

function restoreTokens() {
  if (typeof localStorage === 'undefined') return
  const restored: Record<string, string> = {}
  for (const comment of props.comments.items) {
    const token = localStorage.getItem(tokenKey(comment.id))
    if (token) restored[comment.id] = token
    for (const reply of comment.replies) {
      const replyToken = localStorage.getItem(tokenKey(reply.id))
      if (replyToken) restored[reply.id] = replyToken
    }
  }
  ownedTokens.value = restored
}

function rememberToken(id: string | null | undefined, token: string | null | undefined) {
  if (!id || !token || typeof localStorage === 'undefined') return
  localStorage.setItem(tokenKey(id), token)
  ownedTokens.value = { ...ownedTokens.value, [id]: token }
}

function forgetToken(id: string) {
  if (typeof localStorage !== 'undefined') localStorage.removeItem(tokenKey(id))
  const next = { ...ownedTokens.value }
  delete next[id]
  ownedTokens.value = next
}

function problemMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { _data?: { detail?: string } } })?.response?._data
  return typeof data?.detail === 'string' ? data.detail : fallback
}

function responseStatus(error: unknown) {
  return (error as { response?: { status?: number } })?.response?.status
}

function responseRetryAfter(error: unknown) {
  const value = (error as { response?: { headers?: Headers } })?.response?.headers?.get('Retry-After')
  const seconds = Number(value)
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null
}

async function loadFormContext() {
  if (formContext.value || formStatus.value === 'loading') return
  formStatus.value = 'loading'
  formMessage.value = ''
  try {
    formContext.value = await $fetch<CommentFormContext>(`/api/v1/public/articles/${encodeURIComponent(props.slug)}/comments/form-context`, { credentials: 'include' })
    formReadyAt.value = Date.now() + 3000
    formStatus.value = 'idle'
  } catch (error) {
    formStatus.value = 'error'
    formMessage.value = problemMessage(error, '评论表单暂时无法接收信号，请稍后再试。')
  }
}

function openForm() {
  expanded.value = true
}

function startReply(comment: CommentView) {
  replyTo.value = comment
  openForm()
  void nextTick(() => contentInput.value?.focus())
}

function cancelReply() { replyTo.value = null }

async function ensureFormContext() {
  if (!formContext.value || formContext.value.expiresAt <= new Date().toISOString()) {
    formContext.value = null
    await loadFormContext()
  }
  return formContext.value
}

async function submitComment() {
  if (formStatus.value === 'submitting') return
  if (contentLength.value < 2 || contentLength.value > 2000) {
    formStatus.value = 'error'
    formMessage.value = '正文需要 2–2000 个字符。'
    void nextTick(() => contentInput.value?.focus())
    return
  }
  const readyIn = Math.ceil((formReadyAt.value - Date.now()) / 1000)
  if (readyIn > 0) {
    formStatus.value = 'error'
    formMessage.value = `安全校验还在校准，请再停留 ${readyIn} 秒。`
    return
  }
  const context = await ensureFormContext()
  if (!context) return

  formStatus.value = 'submitting'
  formMessage.value = ''
  retryAfter.value = null
  try {
    const result = await $fetch<Submission>(`/api/v1/public/articles/${encodeURIComponent(props.slug)}/comments`, {
      method: 'POST',
      retry: 0,
      timeout: 8000,
      credentials: 'include',
      headers: { 'X-CSRF-TOKEN': context.csrfToken },
      body: {
        nickname: form.nickname,
        email: form.email.trim() || null,
        content: form.content,
        parentId: replyTo.value?.id || null,
        challenge: context.challenge,
        website: form.website,
      },
    })
    rememberToken(result.id, result.deleteToken)
    formStatus.value = 'published'
    formMessage.value = '评论已发布。'
    form.content = ''
    replyTo.value = null
    formContext.value = null
    formReadyAt.value = 0
    emit('published')
  } catch (error) {
    const status = responseStatus(error)
    retryAfter.value = status === 429 ? responseRetryAfter(error) : null
    formStatus.value = status === 429 ? 'rate-limited' : 'error'
    formMessage.value = status === 429
      ? `信号频率过高，请${retryAfter.value ? `在 ${retryAfter.value} 秒后` : '稍后'}再试。`
      : problemMessage(error, '评论没有送达，请检查输入后再试。')
    if (status === 403) formContext.value = null
  }
}

async function deleteComment(comment: CommentView) {
  const token = ownedTokens.value[comment.id]
  if (!token) return
  if (typeof window !== 'undefined' && !window.confirm('确定删除这条评论吗？删除后公开正文将被移除。')) return
  const context = await ensureFormContext()
  if (!context) return
  try {
    await $fetch(`/api/v1/public/comments/${encodeURIComponent(comment.id)}`, {
      method: 'DELETE',
      retry: 0,
      timeout: 8000,
      credentials: 'include',
      headers: {
        'X-CSRF-TOKEN': context.csrfToken,
        'X-Comment-Delete-Token': token,
      },
    })
    forgetToken(comment.id)
    hiddenIds.value = new Set(hiddenIds.value).add(comment.id)
    actionMessage.value = '评论已从公开信号中移除。'
  } catch (error) {
    formStatus.value = 'error'
    actionMessage.value = problemMessage(error, '删除操作没有完成，请稍后再试。')
  }
}

watch(expanded, value => { if (value && canComment.value) void loadFormContext() })
onMounted(restoreTokens)
</script>

<template>
  <section class="comment-signal" aria-labelledby="comment-signal-title">
    <div class="comment-signal-heading">
      <div>
        <p class="instrument-label">SIGNAL / ECHO</p>
        <h2 id="comment-signal-title">回波信号</h2>
      </div>
      <span class="comment-signal-count" aria-label="评论数量">{{ props.comments.total }} 条</span>
    </div>

    <p v-if="isGloballyClosed" class="comment-signal-note">评论信号已关闭</p>
    <template v-else>
      <p v-if="props.unavailable" class="comment-signal-note" role="status">评论回波暂时失联，文章正文仍可阅读。</p>
      <p v-if="isArticleClosed" class="comment-signal-note">此篇观测已关闭新评论，历史回波仍可读取。</p>
      <p v-else-if="!props.unavailable && !visibleComments.length" class="comment-signal-note">暂未捕获回波。成为第一个留下信号的人。</p>
      <p v-if="actionMessage" class="comment-signal-feedback" role="status" aria-live="polite">{{ actionMessage }}</p>

      <div v-if="visibleComments.length" class="comment-signal-list" aria-label="文章评论">
        <CommentSignalItem
          v-for="comment in visibleComments"
          :key="comment.id"
          :comment="comment"
          :owned-tokens="ownedTokens"
          :hidden-ids="hiddenIds"
          @reply="startReply"
          @delete="deleteComment"
        />
      </div>

      <div v-if="canComment && !props.unavailable" class="comment-signal-compose">
        <button v-if="!expanded" class="comment-signal-expand" type="button" @click="openForm">展开评论入口 <span aria-hidden="true">＋</span></button>
        <form v-else class="comment-signal-form" @submit.prevent="submitComment">
          <p class="comment-signal-form-label">{{ replyTo ? `REPLY / @${replyTo.nickname}` : 'TRANSMIT / NEW ECHO' }}</p>
          <button v-if="replyTo" class="comment-signal-cancel" type="button" @click="cancelReply">取消回复</button>
          <p class="comment-signal-safety">纯文本 · 最多 2000 字 · 仅允许 https 安全链接（最多 3 个）</p>
          <div class="comment-signal-fields">
            <label>昵称<input v-model="form.nickname" name="nickname" required minlength="2" maxlength="40" autocomplete="nickname" spellcheck="false" /></label>
            <label>邮箱（可选）<input v-model="form.email" name="email" type="email" maxlength="254" autocomplete="email" spellcheck="false" /></label>
          </div>
          <label>正文<textarea ref="contentInput" v-model="form.content" name="content" required minlength="2" maxlength="2000" rows="6" aria-describedby="comment-signal-count comment-signal-feedback" /></label>
          <input v-model="form.website" name="website" class="comment-signal-honeypot" tabindex="-1" autocomplete="off" aria-hidden="true" />
          <div class="comment-signal-form-footer">
            <span id="comment-signal-count" class="comment-signal-counter">{{ contentLength }} / 2000</span>
            <button type="submit" :disabled="formStatus === 'loading' || formStatus === 'submitting'">{{ formStatus === 'submitting' ? '发送中…' : '发送回波' }}</button>
          </div>
          <p v-if="formStatus === 'loading'" class="comment-signal-feedback" role="status">正在校准安全表单…</p>
          <p v-else-if="formMessage" id="comment-signal-feedback" class="comment-signal-feedback" :data-status="formStatus" role="status" aria-live="polite">{{ formMessage }}</p>
        </form>
      </div>
    </template>
  </section>
</template>
