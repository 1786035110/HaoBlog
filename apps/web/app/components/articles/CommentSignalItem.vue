<script setup lang="ts">
import type { components } from '@haoblog/api-client'
import CommentSignalText from './CommentSignalText.vue'

type CommentView = components['schemas']['CommentView']

const props = withDefaults(defineProps<{
  comment: CommentView
  depth?: number
  ownedTokens?: Readonly<Record<string, string>>
  hiddenIds?: ReadonlySet<string>
}>(), { depth: 0 })

const emit = defineEmits<{
  reply: [comment: CommentView]
  delete: [comment: CommentView]
}>()

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
})
const formatDate = (value: string) => {
  const parts = Object.fromEntries(dateFormatter.formatToParts(new Date(value)).map(part => [part.type, part.value]))
  return `${parts.year}.${parts.month}.${parts.day}`
}
</script>

<template>
  <article v-if="!props.hiddenIds?.has(props.comment.id)" class="comment-signal-entry" :data-depth="props.depth">
    <span class="comment-signal-point" aria-hidden="true" />
    <div class="comment-signal-entry-body">
      <header class="comment-signal-entry-header">
        <span class="comment-signal-author">{{ props.comment.nickname }}</span>
        <time :datetime="props.comment.createdAt">{{ formatDate(props.comment.createdAt) }}</time>
      </header>
      <p class="comment-signal-content"><CommentSignalText :text="props.comment.content" /></p>
      <div class="comment-signal-actions">
        <button v-if="props.depth === 0" type="button" @click="emit('reply', props.comment)">回复</button>
        <button v-if="props.ownedTokens?.[props.comment.id]" type="button" @click="emit('delete', props.comment)">删除我的评论</button>
      </div>
      <div v-if="props.comment.replies.length" class="comment-signal-replies">
        <CommentSignalItem
          v-for="reply in props.comment.replies"
          :key="reply.id"
          :comment="reply"
          :depth="props.depth + 1"
          :owned-tokens="props.ownedTokens"
          :hidden-ids="props.hiddenIds"
          @delete="emit('delete', $event)"
        />
      </div>
    </div>
  </article>
</template>
