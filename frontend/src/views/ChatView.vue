<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import { watch } from 'vue'
import { useRouter } from 'vue-router'
// Markdown 渲染与 XSS 消毒
// - markdown-it：解析 Markdown → HTML（禁用原始 HTML）
// - DOMPurify：对 HTML 进行消毒，防止 XSS 注入
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
// 代码高亮（使用内置常用语言集合）
import hljs from 'highlight.js/lib/common'
// 主题样式（GitHub 深色风格），也可换用其他主题
import 'highlight.js/styles/github-dark-dimmed.min.css'
import {
  chatSend,
  fetchSessionMessages,
  fetchUserSessions,
  createSession,
  me as apiMe,
  logout,
  getModels,
  clearToken,
  HttpError,
  type ChatMessage,
  type ApiResult,
  type DataMessage,
  type ChatSession,
  type AuthPayload
} from '../api/client'

// 用户信息（可选）
const currentUser = ref<AuthPayload['user'] | null>(null)

// 聊天参数绑定
const userId = ref<number | null>(null)
const sessionId = ref<string>('')
const model = ref<string>('gpt-4o-mini')
const models = ref<string[]>([])
const userInput = ref<string>('')
const placeholderText = ref<string>('发送你的第一条消息…')

// 历史消息与状态
const messages = ref<DataMessage[]>([])
const sessions = ref<ChatSession[]>([])
const loading = ref<boolean>(false)
const errMsg = ref<string>('')
const listEl = ref<HTMLElement | null>(null)
// 本地生成的请求ID（失败时保留，成功后清空）
const pendingRequestId = ref<string>('')

// 结果轮询控制
const polling = ref<boolean>(false)
let pollTimer: number | null = null
let placeholderTimer: number | null = null
const composerEl = ref<HTMLElement | null>(null)
const composerHeight = ref<number>(0)
let composerRO: ResizeObserver | null = null
const showUserMenu = ref<boolean>(false)
const showModelMenu = ref<boolean>(false)
const avatarEl = ref<HTMLElement | null>(null)
const userMenuEl = ref<HTMLElement | null>(null)
let docClickHandler: ((e: MouseEvent) => void) | null = null

const POLL_INTERVAL_MS = 1500
const POLL_TIMEOUT_MS = 60000

const router = useRouter()

// ========== Markdown 渲染器 ==========
// 说明：
// - html: false → 禁止原始 HTML 标签（进一步由 DOMPurify 兜底）
// - linkify: true → 自动将 URL 转为链接
// - breaks: true → 单换行按 <br> 处理，更接近聊天体验
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  // 代码高亮：优先使用声明的语言；否则自动识别；失败时转义为纯文本
  highlight: (code: string, lang: string): string => {
    try {
      if (lang && hljs.getLanguage(lang)) {
        const out = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        return `<pre><code class="hljs language-${lang}">${out}</code></pre>`
      }
      const auto = hljs.highlightAuto(code)
      return `<pre><code class="hljs language-${auto.language || 'text'}">${auto.value}</code></pre>`
    } catch (e) {
      // 使用 markdown-it 的转义能力回退
      // 注意：此处在 md 尚未赋值时不会执行，仅在渲染时执行（运行时 md 已可用）
      return `<pre><code class="hljs">${md.utils.escapeHtml(code)}</code></pre>`
    }
  }
})
// 统一为外链添加 target="_blank" + rel="noopener noreferrer nofollow"
md.renderer.rules.link_open = (tokens: any, idx: number, options: any, env: any, self: any) => {
  const token = tokens[idx]
  const aIndex = token.attrIndex('target')
  if (aIndex < 0) {
    token.attrPush(['target', '_blank'])
  } else {
    token.attrs![aIndex][1] = '_blank'
  }
  const relIndex = token.attrIndex('rel')
  if (relIndex < 0) {
    token.attrPush(['rel', 'noopener noreferrer nofollow'])
  } else {
    token.attrs![relIndex][1] = 'noopener noreferrer nofollow'
  }
  return self.renderToken(tokens, idx, options)
}

/**
 * 将纯文本（LLM 输出）按 Markdown 渲染为安全的 HTML。
 * 入参：text - 原始文本
 * 返回：已消毒的 HTML 字符串，可直接用于 v-html。
 */
function renderMarkdown(text: string | null | undefined): string {
  const raw = String(text ?? '')
  // 先由 markdown-it 解析为 HTML，再用 DOMPurify 消毒（禁止潜在危险标签）
  const html = md.render(raw)
  return DOMPurify.sanitize(html, {
    // 阻止潜在远程/嵌入风险（保持 KISS，后续需要可开白）
    FORBID_TAGS: ['img', 'video', 'audio', 'iframe', 'object', 'embed', 'style']
  })
}

/**
 * 为已渲染的 Markdown 代码块添加“复制”按钮（幂等）。
 * - 选择器：.content.markdown pre
 * - 行为：复制内部 code 的纯文本；成功后短暂显示“已复制”。
 */
function enhanceRenderedMarkdown(): void {
  const root = listEl.value
  if (!root) return
  const pres = root.querySelectorAll<HTMLPreElement>('.content.markdown pre')
  pres.forEach((pre) => {
    if (pre.dataset.enhanced === '1') return
    pre.dataset.enhanced = '1'
    // 按钮
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.className = 'copy-btn'
    btn.textContent = '复制'
    btn.setAttribute('aria-label', '复制代码')
    // 始终可见（视觉低干扰，样式控制在 CSS）
    btn.addEventListener('click', async (e) => {
      e.preventDefault()
      const codeEl = pre.querySelector('code')
      const text = codeEl ? codeEl.innerText : pre.innerText
      try {
        if (navigator.clipboard && window.isSecureContext) {
          await navigator.clipboard.writeText(text)
        } else {
          const ta = document.createElement('textarea')
          ta.value = text
          ta.style.position = 'fixed'
          ta.style.left = '-9999px'
          document.body.appendChild(ta)
          ta.focus()
          ta.select()
          document.execCommand('copy')
          document.body.removeChild(ta)
        }
        const old = btn.textContent
        btn.textContent = '已复制'
        setTimeout(() => { btn.textContent = old || '复制' }, 1500)
      } catch (err) {
        const old = btn.textContent
        btn.textContent = '失败'
        setTimeout(() => { btn.textContent = old || '复制' }, 1500)
      }
    })
    pre.appendChild(btn)
  })
}

/**
 * 滚动到底部（渲染完成后）
 */
async function scrollToBottom(): Promise<void> {
  await nextTick()
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

/**
 * 加载历史消息（使用 sessionId）
 */
async function loadHistory(): Promise<void> {
  errMsg.value = ''
  try {
    if (!sessionId.value) return
    const res: ApiResult<DataMessage[]> = await fetchSessionMessages(sessionId.value)
    messages.value = Array.isArray(res.data) ? res.data : []
    await scrollToBottom()
    // 历史渲染后增强代码块
    enhanceRenderedMarkdown()
  } catch (e: unknown) {
    errMsg.value = e instanceof Error ? e.message : String(e)
  }
}

/**
 * 加载会话列表（按当前用户）
 */
async function loadSessions(): Promise<void> {
  if (userId.value == null) return
  try {
    const res = await fetchUserSessions(userId.value)
    sessions.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    // 忽略错误，显示空列表
    sessions.value = []
  }
}

/**
 * 发送一条用户消息并刷新历史（含 429 处理）
 */
async function onSend(): Promise<void> {
  loading.value = true
  errMsg.value = ''
  const prevCount = messages.value.length
  const text = userInput.value
  try {
    const outgoing: ChatMessage[] = [{ role: 'user', content: text }]
    if (!pendingRequestId.value) {
      pendingRequestId.value = crypto.randomUUID()
    }
    // 等待后端返回（202 或错误）
    await chatSend({
      sessionId: sessionId.value,
      user_id: userId.value as number,
      model: model.value,
      messages: outgoing,
      request_id: pendingRequestId.value
    })

    // 成功：清空输入，开始轮询直到出现 assistant 或超时
    userInput.value = ''
    const startAt = Date.now()
    polling.value = true
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    pollTimer = window.setInterval(async () => {
      try { await loadHistory() } catch { /* 忽略 */ }
      const hasNew = messages.value.length > prevCount
      const last = messages.value[messages.value.length - 1]
      const done = hasNew && last && last.role === 'assistant'
      const timedOut = Date.now() - startAt > POLL_TIMEOUT_MS
      if (done || timedOut) {
        polling.value = false
        loading.value = false
        pendingRequestId.value = ''
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
        if (timedOut) {
          errMsg.value = '生成超时，请稍后重试'
        }
      }
    }, POLL_INTERVAL_MS)
  } catch (e: unknown) {
    // 429：本地回显，不触发轮询
    if (e instanceof HttpError && e.status === 429) {
      messages.value.push({
        sessionId: sessionId.value,
        userId: userId.value as number,
        role: 'user',
        content: text,
        model: model.value,
        createTime: new Date().toISOString()
      })
      await scrollToBottom()
      errMsg.value = '请求过快，已被限流（429）。已在本地显示，未提交到服务器。'
      loading.value = false
      pendingRequestId.value = ''
      return
    }
    // 其他错误
    errMsg.value = e instanceof Error ? e.message : String(e)
    loading.value = false
    pendingRequestId.value = ''
  }
}

/** 退出登录（调用后端接口 + 清理 token 并跳转登录） */
async function onLogout(): Promise<void> {
  try {
    // 1. 调用后端登出接口（将JWT加入黑名单）
    await logout()
    console.log('后端登出成功')
  } catch (err) {
    console.warn('后端登出失败，可能token已过期:', err)
    // 即使后端登出失败也继续执行前端清理
  }

  // 2. 清理前端token
  clearToken()

  // 3. 跳转到登录页
  void router.replace({ name: 'login', query: { redirect: '/chat' } })
}

function ensureSessionForUser(uid: number): void {
  const key = `session:${uid}`
  let sid = localStorage.getItem(key)
  if (!sid) {
    sid = crypto.randomUUID()
    localStorage.setItem(key, sid)
  }
  sessionId.value = sid
}

async function initModels(): Promise<void> {
  try {
    const list = await getModels()
    models.value = Array.isArray(list) && list.length > 0 ? list : ['gpt-4o-mini']
    if (!models.value.includes(model.value)) {
      model.value = models.value[0]
    }
  } catch {
    models.value = ['gpt-4o-mini']
    model.value = 'gpt-4o-mini'
  }
}

async function newSession(): Promise<void> {
  if (userId.value == null) return
  const sid = crypto.randomUUID()
  localStorage.setItem(`session:${userId.value}`, sid)
  sessionId.value = sid
  // 尝试在后端注册会话（可选）
  try {
    await createSession({ sessionId: sid, userId: userId.value, model: model.value })
  } catch {
    // 忽略失败，等首次发消息时由后端懒创建
  }
  await loadSessions()
  await loadHistory()
}

onMounted(() => {
  // 初始化用户信息与会话、模型
  void apiMe()
      .then(async (r) => {
        currentUser.value = r.data
        userId.value = r.data.id
        ensureSessionForUser(r.data.id)
        await initModels(); console.debug('[initModels] models', models.value)
        await loadSessions()
        await loadHistory()
      })
      .catch(() => {
        // 无法获取用户信息，按路由守卫处理
      })

  // 轮播占位提示（仅在输入为空时变更）
  const hints = [
    '试试：给我总结一下今天的待办',
    '问我：如何用 RabbitMQ 做重试队列？',
    '来一个：帮我起一个会话标题',
    '或者：介绍一下这个项目的架构',
    '亦可：/new 创建新会话（点击右上 “新建会话”）'
  ]
  placeholderText.value = hints[Math.floor(Math.random() * hints.length)]
  placeholderTimer = window.setInterval(() => {
    if (userInput.value.trim().length > 0) return
    placeholderText.value = hints[Math.floor(Math.random() * hints.length)]
  }, 4000)

  // 动态测量输入框高度，用于末尾透明占位块（不遮挡最后一条消息）
  const measure = () => {
    const el = composerEl.value
    composerHeight.value = el ? el.offsetHeight : 0
  }
  measure()
  if ('ResizeObserver' in window) {
    composerRO = new ResizeObserver(() => measure())
    if (composerEl.value) composerRO.observe(composerEl.value)
  }
  window.addEventListener('resize', measure)

  // 点击外部关闭用户菜单和模型菜单
  docClickHandler = (e: MouseEvent) => {
    const t = e.target as Node
    const inMenu = userMenuEl.value && userMenuEl.value.contains(t)
    const inAvatar = avatarEl.value && avatarEl.value.contains(t)
    if (!inMenu && !inAvatar) showUserMenu.value = false

    // 检查模型菜单
    const modelMenuEl = document.querySelector('.model-menu')
    const modelTriggerEl = document.querySelector('.model-selector-trigger')
    const inModelMenu = modelMenuEl && modelMenuEl.contains(t)
    const inModelTrigger = modelTriggerEl && modelTriggerEl.contains(t)
    if (!inModelMenu && !inModelTrigger) showModelMenu.value = false
  }
  document.addEventListener('click', docClickHandler)
})

onBeforeUnmount(() => {
  if (placeholderTimer) { clearInterval(placeholderTimer); placeholderTimer = null }
  if (composerRO && composerEl.value) composerRO.unobserve(composerEl.value)
  composerRO = null
  if (docClickHandler) document.removeEventListener('click', docClickHandler)
})

/**
 * 获取模型描述信息
 */
function getModelDescription(modelName: string): string {
  const descriptions: Record<string, string> = {
    'gpt-4o': '最新 GPT-4 模型，支持多模态',
    'gpt-4o-mini': '轻量版 GPT-4，快速响应',
    'gpt-4-turbo': 'GPT-4 Turbo，平衡性能与速度',
    'gpt-3.5-turbo': '经典模型，快速且高效',
    'claude-3-opus': 'Claude 最强模型',
    'claude-3-sonnet': 'Claude 平衡版本',
    'claude-3-haiku': 'Claude 快速版本'
  }
  return descriptions[modelName] || '智能AI助手'
}

/**
 * 格式化时间显示
 */
function formatTime(timeStr: string | null | undefined): string {
  if (!timeStr) return ''
  try {
    const time = new Date(timeStr)
    const now = new Date()
    const diff = now.getTime() - time.getTime()

    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    return time.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

// 监听消息变化，渲染后增强代码块（高亮已由 markdown-it highlight 输出，复制按钮在此挂载）
watch(messages, async () => {
  await nextTick()
  enhanceRenderedMarkdown()
}, { deep: true })
</script>

<template>
  <main class="page">
    <!-- 新设计的顶部栏 -->
    <header class="topbar">
      <div class="topbar-left">
        <div class="logo">🤖 AI Chat</div>
        <div class="session-title">{{ sessions.find(s => s.sessionId === sessionId)?.title || '新会话' }}</div>
      </div>
      <div class="topbar-right">
        <div class="model-selector">
          <div class="model-selector-trigger" @click="showModelMenu = !showModelMenu">
            <svg class="model-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7v10c0 5.55 3.84 10 9 10s9-4.45 9-10V7L12 2z"/>
              <path d="M12 8v8"/>
              <path d="M8 12h8"/>
            </svg>
            <span class="model-name">{{ model }}</span>
            <svg class="chevron-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6"/>
            </svg>
          </div>
          <div v-if="showModelMenu" class="model-menu">
            <div class="model-menu-header">选择模型</div>
            <div
              v-for="m in models"
              :key="m"
              class="model-option"
              :class="{ active: m === model }"
              @click="model = m; showModelMenu = false"
            >
              <div class="model-info">
                <div class="model-title">{{ m }}</div>
                <div class="model-desc">{{ getModelDescription(m) }}</div>
              </div>
              <div v-if="m === model" class="check-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 6L9 17l-5-5"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        <button class="new-chat-btn" @click="newSession" title="新建会话">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
        </button>
        <div class="user-avatar" ref="avatarEl" @click="showUserMenu = !showUserMenu">
          {{ (currentUser?.username || 'U').charAt(0).toUpperCase() }}
        </div>
        <div v-if="showUserMenu" class="user-menu" ref="userMenuEl">
          <div class="user-info">
            <div class="username">{{ currentUser?.username || '未登录' }}</div>
            <div class="email">{{ currentUser?.email || '' }}</div>
          </div>
          <button class="logout-btn" @click="onLogout">退出登录</button>
        </div>
      </div>
    </header>

    <div class="body">
      <!-- 重新设计的侧边栏 -->
      <aside class="sidebar" :class="{ 'sidebar-collapsed': false }">
        <div class="sidebar-header">
          <h3>对话历史</h3>
        </div>
        <ul class="session-list">
          <li
            v-for="s in sessions"
            :key="s.sessionId"
            :class="{ active: s.sessionId === sessionId }"
            @click="sessionId = s.sessionId; loadHistory()"
          >
            <div class="session-icon">💬</div>
            <div class="session-content">
              <div class="session-title">{{ s.title || s.sessionId.slice(0, 8) + '...' }}</div>
              <div class="session-meta">{{ s.model || 'gpt-4o-mini' }}</div>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 全新的聊天区域 -->
      <section class="chat-container">
        <div class="messages-wrapper" ref="listEl">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="empty-icon">🌟</div>
            <h3>开始新的对话</h3>
            <p>问我任何问题，我会尽力帮助你</p>
          </div>

          <div v-for="(m, idx) in messages" :key="idx" class="message-group" :class="m.role">
            <div class="message-avatar">
              <div v-if="m.role === 'user'" class="user-avatar-msg">
                {{ (currentUser?.username || 'U').charAt(0).toUpperCase() }}
              </div>
              <div v-else class="ai-avatar-msg">🤖</div>
            </div>

            <div class="message-content">
              <div class="message-bubble">
                <div v-if="m.role !== 'assistant'" class="text-content">{{ m.content }}</div>
                <div v-else class="markdown-content" v-html="renderMarkdown(m.content)"></div>
              </div>
              <div class="message-time">{{ formatTime(m.createTime) }}</div>
            </div>
          </div>

          <!-- 底部占位空间，给最后一条消息留出呼吸空间 -->
          <div class="spacer" :style="{ height: Math.max(composerHeight + 32, 120) + 'px' }"></div>
        </div>

        <!-- 全新的输入框设计 -->
        <div class="composer-container" ref="composerEl">
          <div class="composer">
            <div class="composer-input">
              <textarea
                v-model="userInput"
                :placeholder="placeholderText"
                @keydown.enter.exact.prevent="!loading && userInput.trim() && onSend()"
                @keydown.enter.shift.exact="userInput += '\n'"
              ></textarea>
              <button
                class="send-button"
                :disabled="loading || !userInput.trim()"
                @click="onSend"
              >
                <svg v-if="!loading" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="m22 2-7 20-4-9-9-4z"/>
                  <path d="M22 2 11 13"/>
                </svg>
                <div v-else class="loading-spinner"></div>
              </button>
            </div>
            <div v-if="errMsg" class="error-message">{{ errMsg }}</div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
/* 现代化布局 */
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

/* 顶部栏重新设计 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--border);
  position: relative;
  z-index: 100;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.logo {
  font-size: 1.25rem;
  font-weight: 700;
  background: var(--warm-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.session-title {
  font-size: 0.9rem;
  color: var(--muted);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  position: relative;
}

/* 现代化模型选择器 */
.model-selector {
  position: relative;
}

.model-selector-trigger {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  font-size: 0.9rem;
}

.model-selector-trigger:hover {
  border-color: var(--primary);
  box-shadow: 0 2px 8px rgba(249,115,22,0.15);
  transform: translateY(-1px);
}

.model-icon {
  color: var(--primary);
  flex-shrink: 0;
}

.model-name {
  font-weight: 500;
  color: var(--text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron-icon {
  color: var(--muted);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.model-selector-trigger:hover .chevron-icon {
  transform: translateY(1px);
}

.model-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 280px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.15);
  z-index: 1000;
  overflow: hidden;
  animation: modelMenuSlideIn 0.2s ease-out;
}

@keyframes modelMenuSlideIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.model-menu-header {
  padding: 0.875rem 1rem;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text);
  background: color-mix(in oklab, var(--card), var(--accent-light) 10%);
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.875rem 1rem;
  cursor: pointer;
  transition: all 0.15s ease;
  border-bottom: 1px solid rgba(0,0,0,0.03);
}

.model-option:last-child {
  border-bottom: none;
}

.model-option:hover {
  background: color-mix(in oklab, var(--card), var(--accent-light) 15%);
}

.model-option.active {
  background: linear-gradient(135deg,
    color-mix(in oklab, var(--card), var(--accent-light) 25%) 0%,
    color-mix(in oklab, var(--card), var(--primary) 8%) 100%);
  border-left: 3px solid var(--primary);
}

.model-info {
  flex: 1;
  min-width: 0;
}

.model-title {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text);
  margin-bottom: 0.25rem;
}

.model-desc {
  font-size: 0.8rem;
  color: var(--muted);
  line-height: 1.3;
}

.check-icon {
  color: var(--primary);
  flex-shrink: 0;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: var(--card);
  color: var(--primary);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.new-chat-btn:hover {
  background: var(--primary);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(249,115,22,0.25);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--warm-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(249,115,22,0.25);
}

.user-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(249,115,22,0.35);
}

.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  min-width: 200px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0,0,0,0.1);
  padding: 1rem;
  z-index: 1000;
}

.user-info {
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}

.username {
  font-weight: 600;
  color: var(--text);
  margin-bottom: 0.25rem;
}

.email {
  font-size: 0.85rem;
  color: var(--muted);
}

.logout-btn {
  width: 100%;
  padding: 0.5rem;
  background: var(--warm-gradient);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(249,115,22,0.25);
}

/* 主体布局 */
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* 重新设计的侧边栏 */
.sidebar {
  width: 280px;
  background: linear-gradient(180deg,
    color-mix(in oklab, var(--surface), var(--accent-light) 3%) 0%,
    var(--surface) 100%);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 1.5rem 1rem 1rem;
  border-bottom: 1px solid var(--border);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text);
}

.session-list {
  flex: 1;
  padding: 0.5rem;
  overflow-y: auto;
  list-style: none;
  margin: 0;
}

.session-list li {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  margin-bottom: 0.25rem;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.session-list li:hover {
  background: color-mix(in oklab, var(--card), var(--accent-light) 20%);
  border-color: color-mix(in oklab, var(--border), var(--accent) 30%);
  transform: translateX(2px);
}

.session-list li.active {
  background: linear-gradient(135deg,
    color-mix(in oklab, var(--card), var(--accent-light) 40%) 0%,
    color-mix(in oklab, var(--card), var(--primary) 10%) 100%);
  border-color: var(--primary);
  box-shadow: 0 2px 8px rgba(249,115,22,0.15);
}

.session-icon {
  font-size: 1.2rem;
  opacity: 0.8;
}

.session-content {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-weight: 500;
  font-size: 0.9rem;
  color: var(--text);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  font-size: 0.75rem;
  color: var(--muted);
}

/* 聊天容器重新设计 */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  position: relative;
}

.messages-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 2rem;
  scroll-behavior: smooth;
}

/* 空状态设计 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  text-align: center;
  color: var(--muted);
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
  opacity: 0.8;
}

.empty-state h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text);
}

.empty-state p {
  margin: 0;
  font-size: 1rem;
}

/* 消息组重新设计 */
.message-group {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  animation: messageSlideIn 0.3s ease-out;
}

.message-group.user {
  flex-direction: row-reverse;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-avatar {
  flex-shrink: 0;
}

.user-avatar-msg, .ai-avatar-msg {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.9rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.user-avatar-msg {
  background: var(--warm-gradient);
  color: white;
}

.ai-avatar-msg {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 1.2rem;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-bubble {
  background: var(--card);
  border-radius: 16px;
  padding: 1rem 1.25rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  border: 1px solid var(--border);
  transition: all 0.2s ease;
  position: relative;
}

.message-group.user .message-bubble {
  background: var(--warm-gradient);
  color: white;
  border: none;
  box-shadow: 0 4px 12px rgba(249,115,22,0.25);
}

.message-bubble:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}

.message-group.user .message-bubble:hover {
  box-shadow: 0 8px 20px rgba(249,115,22,0.35);
}

.text-content, .markdown-content {
  line-height: 1.6;
  word-wrap: break-word;
}

.markdown-content {
  color: inherit;
}

.markdown-content h1, .markdown-content h2, .markdown-content h3,
.markdown-content h4, .markdown-content h5, .markdown-content h6 {
  margin: 1rem 0 0.5rem 0;
  font-weight: 600;
}

.markdown-content p {
  margin: 0.5rem 0;
}

.markdown-content ul, .markdown-content ol {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
}

.markdown-content li {
  margin: 0.25rem 0;
}

.markdown-content pre {
  background: #1a1a1a;
  color: #e1e1e1;
  border-radius: 8px;
  padding: 1rem;
  margin: 1rem 0;
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 0.9rem;
}

.markdown-content code {
  background: rgba(0,0,0,0.05);
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 0.9rem;
}

.message-group.user .markdown-content code {
  background: rgba(255,255,255,0.15);
}

.markdown-content pre code {
  background: transparent;
  padding: 0;
}

.markdown-content a {
  color: var(--primary);
  text-decoration: underline;
}

.message-group.user .markdown-content a {
  color: rgba(255,255,255,0.9);
}

.markdown-content blockquote {
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  border-left: 4px solid var(--primary);
  background: color-mix(in oklab, var(--card), var(--accent-light) 30%);
  border-radius: 0 8px 8px 0;
}

.message-time {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: var(--muted);
  opacity: 0.8;
}

.message-group.user .message-time {
  text-align: right;
}

.spacer {
  width: 100%;
}

/* 全新输入框设计 */
.composer-container {
  padding: 1.5rem 2rem 2rem;
  background: linear-gradient(to top,
    var(--bg) 0%,
    color-mix(in oklab, var(--bg), transparent 20%) 100%);
  border-top: 1px solid var(--border);
}

.composer {
  max-width: 800px;
  margin: 0 auto;
}

.composer-input {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  background: var(--card);
  border: 2px solid var(--border);
  border-radius: 16px;
  padding: 0.75rem;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.composer-input:focus-within {
  border-color: var(--primary);
  box-shadow: 0 8px 24px rgba(249,115,22,0.15);
}

.composer-input textarea {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text);
  font-size: 1rem;
  line-height: 1.5;
  resize: none;
  max-height: 120px;
  min-height: 40px;
  overflow-y: auto;
}

.composer-input textarea::placeholder {
  color: var(--muted);
}

.send-button {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: var(--warm-gradient);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.send-button:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(249,115,22,0.4);
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-radius: 50%;
  border-top-color: white;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.error-message {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border-radius: 8px;
  font-size: 0.9rem;
  border-left: 4px solid #dc2626;
}

/* 代码块复制按钮样式 */
.markdown-content pre :deep(.copy-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.8);
  border: none;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.markdown-content pre :deep(.copy-btn):hover {
  background: rgba(255,255,255,0.2);
  color: white;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .body {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    max-height: 200px;
  }

  .messages-wrapper {
    padding: 1rem;
  }

  .composer-container {
    padding: 1rem;
  }

  .message-group {
    margin-bottom: 1.5rem;
  }

  .user-avatar-msg, .ai-avatar-msg {
    width: 36px;
    height: 36px;
  }
}
</style>
