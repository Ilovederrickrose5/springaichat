import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Chat from '@/views/Chat.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/chat',
    name: 'Chat',
    component: Chat
  },
  {
    path: '/',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 读 accessToken：优先新字段 accessToken，兼容旧字段 token
function getAccessToken() {
  return localStorage.getItem('accessToken') || localStorage.getItem('token') || ''
}

router.beforeEach((to, from, next) => {
  const token = getAccessToken()

  if (to.path === '/login') {
    if (token) {
      next('/chat')
    } else {
      next()
    }
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
