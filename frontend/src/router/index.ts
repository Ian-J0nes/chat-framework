import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '../api/client'

// 路由组件按需引入，保持最小体积
const AuthView = () => import('../views/AuthView.vue')
const ChatView = () => import('../views/ChatView.vue')

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/chat' },
  { path: '/login', name: 'login', component: AuthView, meta: { public: true } },
  { path: '/register', name: 'register', component: AuthView, meta: { public: true, register: true } },
  { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 简单鉴权：仅检查本地是否存在 token
router.beforeEach((to, _from, next) => {
  const token = getToken()
  const requiresAuth = Boolean(to.meta?.requiresAuth)
  const isAuthPage = to.name === 'login' || to.name === 'register'

  if (requiresAuth && !token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  if (isAuthPage && token) {
    const redirect = (to.query?.redirect as string) || '/chat'
    next(redirect)
    return
  }

  next()
})

export default router

