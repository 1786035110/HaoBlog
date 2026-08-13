<template>
  <section class="observation-scene studio-scene" aria-labelledby="studio-title">
    <p class="instrument-label">STUDIO / AUTHENTICATION FRAME</p>
    <h1 id="studio-title">校准入口</h1>
    <p class="signal-copy">管理员会话只保存在安全 Cookie 中。先校准 CSRF 信号，再进入工作台。</p>

    <div v-if="session" class="studio-console" aria-live="polite">
      <p class="console-label">SESSION / ACTIVE</p>
      <p class="session-name">{{ session.username }}</p>
      <p class="signal-note">权限：{{ session.role }} · 会话已恢复</p>
      <p v-if="error" class="form-error" role="alert" aria-live="assertive">{{ error }}</p>
      <button class="instrument-button" type="button" :disabled="pending" @click="logout">
        {{ pending ? 'SIGNAL CLEARING…' : '退出会话' }}
      </button>
    </div>

    <form v-else class="studio-console" @submit.prevent="submit">
      <p class="console-label">SESSION / REQUEST ACCESS</p>
      <div class="field-line">
        <label for="admin-username">管理员标识</label>
        <input id="admin-username" v-model="credentials.username" name="username" autocomplete="username" required maxlength="64">
      </div>
      <div class="field-line">
        <label for="admin-password">访问密钥</label>
        <input id="admin-password" v-model="credentials.password" name="password" type="password" autocomplete="current-password" required maxlength="200">
      </div>
      <p v-if="error" class="form-error" role="alert" aria-live="assertive">{{ error }}</p>
      <button class="instrument-button" type="submit" :disabled="pending">
        {{ pending ? 'CALIBRATING…' : '建立安全会话' }}
      </button>
    </form>
  </section>
</template>

<script setup lang="ts">
import type { paths } from '@haoblog/api-client'

type LoginPayload = paths['/api/v1/admin/session']['post']['requestBody']['content']['application/json']

const { session, pending, error, restore, login: signIn, logout } = useAdminSession()
const credentials = reactive<LoginPayload>({ username: '', password: '' })

onMounted(() => restore())

async function submit() {
  const success = await signIn(credentials)
  if (success) credentials.password = ''
}
</script>

<style scoped>
.studio-scene { max-width: 48rem; }
.studio-console {
  display: grid;
  gap: var(--space-6);
  max-width: 32rem;
  margin-top: var(--space-8);
  padding: var(--space-6) 0 var(--space-6) var(--space-6);
  border-left: 2px solid var(--color-accent);
  background: linear-gradient(90deg, color-mix(in srgb, var(--color-accent) 7%, transparent), transparent 70%);
}
.console-label, .field-line label {
  color: var(--color-accent);
  font: var(--text-xs)/1.4 var(--font-mono);
  letter-spacing: .12em;
  text-transform: uppercase;
}
.field-line { display: grid; gap: var(--space-2); }
.field-line input {
  width: 100%;
  padding: .65rem 0;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  outline: 0;
  background: transparent;
  color: var(--color-text-main);
  font: var(--text-base)/1.4 var(--font-mono);
}
.field-line input:focus { border-bottom-color: var(--color-accent); }
.instrument-button {
  width: fit-content;
  padding: .7rem 1rem;
  border: 1px solid var(--color-accent);
  background: transparent;
  color: var(--color-accent);
  cursor: pointer;
  font: var(--text-xs)/1 var(--font-mono);
  letter-spacing: .08em;
}
.instrument-button:hover:not(:disabled), .instrument-button:focus-visible { background: var(--color-accent); color: var(--color-bg-base); }
.instrument-button:disabled { cursor: wait; opacity: .55; }
.form-error { margin: 0; color: var(--color-warn); font: var(--text-sm)/1.5 var(--font-mono); }
.session-name { margin: 0; color: var(--color-text-main); font: clamp(1.5rem, 5vw, 2.5rem)/1 var(--font-display); }
@media (max-width: 360px) { .studio-console { padding-left: var(--space-3); } }
</style>
