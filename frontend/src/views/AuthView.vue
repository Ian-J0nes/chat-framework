<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { register as apiRegister, login as apiLogin, setToken, type AuthPayload } from '../api/client'

// 表单状态
const isRegister = ref<boolean>(false)
const formUsername = ref<string>('')
const formEmail = ref<string>('')
const formPassword = ref<string>('')
const formNickname = ref<string>('')
const authError = ref<string>('')

const route = useRoute()
const router = useRouter()

onMounted(() => {
  // 若访问 /register 则默认注册模式
  isRegister.value = route.name === 'register'
})

/**
 * 完成认证后的重定向
 * 入参：payload（后端返回的认证载荷）
 * 出参：无（副作用：跳转到 redirect 或 /chat）
 */
function afterAuth(payload: AuthPayload): void {
  // 保存 token 并跳转
  setToken(payload.token)
  const redirect = (route.query?.redirect as string) || '/chat'
  void router.replace(redirect)
}

/**
 * 注册：POST /api/user/register
 * 入参：无（使用表单数据）
 * 出参：无（副作用：保存 token 并跳转）
 */
async function onRegister(): Promise<void> {
  authError.value = ''
  try {
    const res = await apiRegister({
      username: formUsername.value,
      email: formEmail.value,
      password: formPassword.value,
      nickname: formNickname.value || undefined
    })
    afterAuth(res.data)
  } catch (e: unknown) {
    authError.value = e instanceof Error ? e.message : String(e)
  }
}

/**
 * 登录：POST /api/user/login
 * 入参：无（使用表单数据）
 * 出参：无（副作用：保存 token 并跳转）
 */
async function onLogin(): Promise<void> {
  authError.value = ''
  try {
    // 将“账号”字段合并：含 @ 视为 email，否则视为 username
    const account = formUsername.value || formEmail.value
    const payload = (account && account.includes('@'))
      ? { email: account, password: formPassword.value }
      : { username: account, password: formPassword.value }
    const res = await apiLogin(payload as any)
    afterAuth(res.data)
  } catch (e: unknown) {
    authError.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-container">
      <!-- 品牌区域 -->
      <div class="brand-section">
        <div class="brand-logo">🤖</div>
        <h1 class="brand-title">AI Chat</h1>
        <p class="brand-subtitle">智能对话，创造无限可能</p>
      </div>

      <!-- 表单区域 -->
      <div class="form-section">
        <div class="form-header">
          <h2>{{ isRegister ? '创建账户' : '欢迎回来' }}</h2>
          <p class="form-subtitle">
            {{ isRegister ? '加入我们，开启 AI 对话之旅' : '登录您的账户继续对话' }}
          </p>
        </div>

        <form class="auth-form" @submit.prevent>
          <!-- 登录表单 -->
          <template v-if="!isRegister">
            <div class="form-group">
              <label for="account">账号</label>
              <div class="input-wrapper">
                <svg class="input-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <input
                  id="account"
                  type="text"
                  v-model="formUsername"
                  placeholder="用户名或邮箱"
                  required
                />
              </div>
            </div>
          </template>

          <!-- 注册表单 -->
          <template v-else>
            <div class="form-group">
              <label for="username">用户名</label>
              <div class="input-wrapper">
                <svg class="input-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <input
                  id="username"
                  type="text"
                  v-model="formUsername"
                  placeholder="输入用户名"
                  required
                />
              </div>
            </div>

            <div class="form-group">
              <label for="email">邮箱</label>
              <div class="input-wrapper">
                <svg class="input-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                  <polyline points="22,6 12,13 2,6"/>
                </svg>
                <input
                  id="email"
                  type="email"
                  v-model="formEmail"
                  placeholder="输入邮箱地址"
                  required
                />
              </div>
            </div>
          </template>

          <!-- 密码输入 -->
          <div class="form-group">
            <label for="password">密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <circle cx="12" cy="16" r="1"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input
                id="password"
                type="password"
                v-model="formPassword"
                :placeholder="isRegister ? '创建密码' : '输入密码'"
                required
              />
            </div>
          </div>

          <!-- 昵称（注册时） -->
          <div v-if="isRegister" class="form-group">
            <label for="nickname">昵称（可选）</label>
            <div class="input-wrapper">
              <svg class="input-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
              </svg>
              <input
                id="nickname"
                type="text"
                v-model="formNickname"
                placeholder="输入昵称"
              />
            </div>
          </div>

          <!-- 错误信息 -->
          <div v-if="authError" class="error-alert">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
            {{ authError }}
          </div>

          <!-- 提交按钮 -->
          <button type="submit" class="submit-btn" @click="isRegister ? onRegister() : onLogin()">
            <span class="btn-text">{{ isRegister ? '注册账户' : '立即登录' }}</span>
            <svg class="btn-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </button>

          <!-- 切换链接 -->
          <div class="switch-mode">
            <span>{{ isRegister ? '已有账户？' : '还没有账户？' }}</span>
            <button type="button" class="switch-btn" @click="isRegister = !isRegister">
              {{ isRegister ? '立即登录' : '创建账户' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>
  </div>
</template>

<style scoped>
/* 全屏布局 */
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  position: relative;
  overflow: hidden;
}

/* 主容器 */
.auth-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  max-width: 1000px;
  width: 100%;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px) saturate(180%);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  position: relative;
  z-index: 10;
}

/* 品牌区域 */
.brand-section {
  background: var(--warm-gradient);
  color: white;
  padding: 3rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.brand-section::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(45deg,
    transparent 0%,
    rgba(255,255,255,0.1) 50%,
    transparent 100%);
  animation: shimmer 3s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { transform: translateX(-100%) translateY(-100%); }
  50% { transform: translateX(0%) translateY(0%); }
}

.brand-logo {
  font-size: 4rem;
  margin-bottom: 1rem;
  text-shadow: 0 4px 8px rgba(0,0,0,0.2);
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

.brand-title {
  font-size: 2.5rem;
  font-weight: 700;
  margin: 0 0 0.5rem 0;
  letter-spacing: -0.5px;
}

.brand-subtitle {
  font-size: 1.1rem;
  opacity: 0.9;
  margin: 0;
  font-weight: 300;
}

/* 表单区域 */
.form-section {
  padding: 3rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-header {
  margin-bottom: 2rem;
  text-align: center;
}

.form-header h2 {
  font-size: 2rem;
  font-weight: 700;
  margin: 0 0 0.5rem 0;
  background: var(--warm-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.form-subtitle {
  color: var(--muted);
  margin: 0;
  font-size: 1rem;
}

/* 表单样式 */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  font-weight: 600;
  color: var(--text);
  font-size: 0.9rem;
  letter-spacing: 0.5px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 1rem;
  color: var(--muted);
  z-index: 1;
  transition: color 0.2s ease;
}

.input-wrapper input {
  width: 100%;
  padding: 1rem 1rem 1rem 3rem;
  border: 2px solid var(--border);
  border-radius: 12px;
  background: var(--card);
  color: var(--text);
  font-size: 1rem;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.input-wrapper input:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 4px rgba(249,115,22,0.1);
}

.input-wrapper input:focus + .input-icon,
.input-wrapper:focus-within .input-icon {
  color: var(--primary);
}

.input-wrapper input::placeholder {
  color: var(--muted);
}

/* 错误提示 */
.error-alert {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 12px;
  color: #dc2626;
  font-size: 0.9rem;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.error-alert svg {
  flex-shrink: 0;
}

/* 提交按钮 */
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  width: 100%;
  padding: 1rem;
  background: var(--warm-gradient);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 8px 20px rgba(249,115,22,0.25);
  position: relative;
  overflow: hidden;
}

.submit-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(255,255,255,0.2) 50%,
    transparent 100%);
  transition: left 0.5s ease;
}

.submit-btn:hover::before {
  left: 100%;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(249,115,22,0.35);
  filter: saturate(110%) brightness(105%);
}

.submit-btn:active {
  transform: translateY(0);
  box-shadow: 0 6px 16px rgba(249,115,22,0.3);
}

.btn-text {
  flex: 1;
}

.btn-icon {
  transition: transform 0.2s ease;
}

.submit-btn:hover .btn-icon {
  transform: translateX(2px);
}

/* 切换模式 */
.switch-mode {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem 0 0;
  border-top: 1px solid var(--border);
  font-size: 0.95rem;
  color: var(--muted);
}

.switch-btn {
  background: none;
  border: none;
  color: var(--primary);
  font-weight: 600;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
  transition: all 0.2s ease;
  position: relative;
}

.switch-btn:hover {
  background: rgba(249,115,22,0.1);
  color: var(--primary-600);
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
}

.circle {
  position: absolute;
  border-radius: 50%;
  background: var(--warm-gradient);
  opacity: 0.1;
  animation: floatCircle 6s ease-in-out infinite;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -150px;
  right: -150px;
  animation-delay: 0s;
}

.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -100px;
  left: -100px;
  animation-delay: 2s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  left: 10%;
  animation-delay: 4s;
}

@keyframes floatCircle {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .auth-container {
    grid-template-columns: 1fr;
    max-width: 400px;
    margin: 1rem;
  }

  .brand-section {
    padding: 2rem;
  }

  .brand-logo {
    font-size: 3rem;
  }

  .brand-title {
    font-size: 2rem;
  }

  .brand-subtitle {
    font-size: 1rem;
  }

  .form-section {
    padding: 2rem;
  }

  .form-header h2 {
    font-size: 1.75rem;
  }

  .circle-1, .circle-2, .circle-3 {
    opacity: 0.05;
  }
}

@media (max-width: 480px) {
  .auth-container {
    margin: 0.5rem;
    border-radius: 16px;
  }

  .brand-section,
  .form-section {
    padding: 1.5rem;
  }

  .auth-form {
    gap: 1.25rem;
  }

  .input-wrapper input {
    padding: 0.875rem 0.875rem 0.875rem 2.75rem;
  }

  .submit-btn {
    padding: 0.875rem;
    font-size: 1rem;
  }
}
</style>
