/**
 * API 客户端（UTF-8，无乱码）
 * - 强类型 + 简明注释（KISS）
 */

export type ChatRole = 'user' | 'assistant' | 'system'

/**
 * HTTP 错误（带状态码与响应文本）。
 * 用于前端区分 429（限流）等场景。
 */
export class HttpError extends Error {
  public readonly status: number
  public readonly body: string
  constructor(status: number, body: string) {
    super(`HTTP ${status}: ${body}`)
    this.name = 'HttpError'
    this.status = status
    this.body = body
  }
}

// ===== 公共类型 =====

export interface ChatMessage {
  /** 角色：user | assistant | system */
  role: ChatRole
  /** 文本内容 */
  content: string
}

export interface ChatRequest {
  /** 业务会话ID（必填） */
  sessionId: string
  /** 用户ID（必填） */
  user_id: number
  /** 模型名称（必填） */
  model: string
  /** 对话消息（至少包含一条 user） */
  messages: ChatMessage[]
  /** 幂等请求ID（可选，建议 UUID） */
  request_id?: string
}

export interface TokenUsage {
  /** 输入 tokens */
  prompt_tokens: number
  /** 输出 tokens */
  completion_tokens: number
  /** 总 tokens */
  total_tokens: number
}

export interface ChatResponse {
  /** 模型回复文本 */
  response: string
  /** 使用的模型 */
  model: string
  /** token 用量（可选） */
  usage?: TokenUsage
  /** 会话ID（可选） */
  sessionId?: string
}

export interface ApiResult<T> {
  /** 状态码，例如 200 */
  code: number
  /** 提示信息 */
  message: string
  /** 数据部分 */
  data: T
}

/**
 * 解析 API 基址
 * - 优先使用环境变量 `VITE_API_BASE`（例如 https://example.com/api）
 * - 其余情况直接指向网关地址（绕过 Vite 代理问题）
 */
const API_BASE: string = (import.meta.env.VITE_API_BASE as string) || 'http://127.0.0.1:18081/api'
/**
 * LLM 专用基址
 * - 可通过 `VITE_LLM_API_BASE` 直连 Python 服务
 * - 默认与 API_BASE 一致
 */
const LLM_API_BASE: string = (import.meta.env.VITE_LLM_API_BASE as string) || API_BASE

// ===== 认证类型 =====

export interface RegisterRequest {
  /** 用户名（必填） */
  username: string
  /** 邮箱（必填） */
  email: string
  /** 明文密码（必填） */
  password: string
  /** 昵称（可选） */
  nickname?: string
}

export interface LoginRequest {
  /** 用户名（与 email 二选一） */
  username?: string
  /** 邮箱（与 username 二选一） */
  email?: string
  /** 明文密码（必填） */
  password: string
}

export interface AuthPayload {
  /** JWT 字符串 */
  token: string
  /** 用户概要信息 */
  user: {
    id: number
    username: string
    email: string
    nickname?: string | null
    avatar?: string | null
  }
}

/** 本地存储 Token（MVP：localStorage） */
const TOKEN_KEY = 'auth_token'
export function setToken(token: string): void { localStorage.setItem(TOKEN_KEY, token) }
export function getToken(): string | null { return localStorage.getItem(TOKEN_KEY) }
export function clearToken(): void { localStorage.removeItem(TOKEN_KEY) }

// ===== 模型列表 =====

/**
 * 获取可用模型列表。
 * - 兼容多种后端返回结构：{data:{models_detail:[]}} / {data:{models:[]}} / 直接数组
 */
export async function getModels(): Promise<string[]> {
  const url = `${LLM_API_BASE}/llm/models`
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { headers })
  if (!resp.ok) {
    try { console.debug('[getModels] body', await resp.text()) } catch {}
    return []
  }
  const json = await resp.json()
  const data = (json && (json as any).data) ?? json
  if (Array.isArray(data)) {
    return data.map((m: any) => (typeof m === 'string' ? m : m?.id)).filter(Boolean)
  }
  if (data && Array.isArray((data as any).models_detail)) {
    return (data as any).models_detail.map((m: any) => (typeof m === 'string' ? m : m?.id)).filter(Boolean)
  }
  if (data && Array.isArray((data as any).models)) {
    return (data as any).models.filter((x: any) => typeof x === 'string')
  }
  return []
}

// ===== 认证 API =====

/** 注册：POST /api/user/register */
export async function register(req: RegisterRequest): Promise<ApiResult<AuthPayload>> {
  const url = `${API_BASE}/user/register`
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(req)
  })
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<AuthPayload>
}

/** 登录：POST /api/user/login */
export async function login(req: LoginRequest): Promise<ApiResult<AuthPayload>> {
  const url = `${API_BASE}/user/login`
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(req)
  })
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<AuthPayload>
}

/** 获取当前用户：GET /api/user/me（需要 Authorization） */
export async function me(): Promise<ApiResult<AuthPayload['user']>> {
  const url = `${API_BASE}/user/me`
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { headers })
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<AuthPayload['user']>
}

/** 登出：POST /api/user/logout（需要 Authorization） */
export async function logout(): Promise<ApiResult<string>> {
  const url = `${API_BASE}/user/logout`
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { method: 'POST', headers })
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<string>
}

// ===== 发送聊天 =====

/**
 * 发送聊天请求（服务端异步处理，成功返回 202）。
 * 非 2xx 抛出 HttpError（含 429）。401 会清理 token 并跳转到登录。
 */
export async function chatSend(req: ChatRequest): Promise<ApiResult<ChatResponse>> {
  const url = `${API_BASE}/chat/send`
  const token = getToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { method: 'POST', headers, body: JSON.stringify(req) })
  if (resp.status === 401) {
    clearToken()
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
    throw new Error('Unauthorized')
  }
  if (!resp.ok) { throw new HttpError(resp.status, await resp.text()) }
  return (await resp.json()) as ApiResult<ChatResponse>
}

// ===== 历史与会话 =====

export type DataMessageRole = 'user' | 'assistant' | 'system'

export interface DataMessage {
  /** 业务会话ID */
  sessionId: string
  /** 用户ID */
  userId: number
  /** 角色 */
  role: DataMessageRole
  /** 文本内容 */
  content: string
  /** 模型（可选） */
  model?: string
  /** 创建时间（可选） */
  createTime?: string
}

/** 获取历史消息列表 */
export async function fetchSessionMessages(sessionId: string): Promise<ApiResult<DataMessage[]>> {
  const url = `${API_BASE}/data/sessions/${encodeURIComponent(sessionId)}/messages`
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { headers })
  if (resp.status === 401) {
    clearToken()
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
    throw new Error('Unauthorized')
  }
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<DataMessage[]>
}

export interface ChatSession {
  /** 数据库主键（可选） */
  id?: number
  /** 业务会话ID */
  sessionId: string
  /** 用户ID */
  userId: number
  /** 模型（可选） */
  model?: string
  /** 标题（可选） */
  title?: string | null
  /** 创建/更新时间（可选） */
  createTime?: string
  updateTime?: string
}

/** 按用户获取会话列表 */
export async function fetchUserSessions(userId: number): Promise<ApiResult<ChatSession[]>> {
  const url = `${API_BASE}/data/users/${encodeURIComponent(String(userId))}/sessions`
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { headers })
  if (resp.status === 401) {
    clearToken()
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
    throw new Error('Unauthorized')
  }
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<ChatSession[]>
}

/** 创建会话 */
export async function createSession(session: ChatSession): Promise<ApiResult<ChatSession>> {
  const url = `${API_BASE}/data/sessions`
  const token = getToken()
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const resp = await fetch(url, { method: 'POST', headers, body: JSON.stringify(session) })
  if (resp.status === 401) {
    clearToken()
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
    throw new Error('Unauthorized')
  }
  if (!resp.ok) { throw new Error(`HTTP ${resp.status}: ${await resp.text()}`) }
  return (await resp.json()) as ApiResult<ChatSession>
}

