import axios from 'axios'
import router from '../router'
import BASE_URL from '../api/config'
import { refreshToken as refreshAccessToken } from './auth'

// ===== 本地存储 key =====
const TOKEN_KEY = 'token'
const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_ID_KEY = 'userId'
const USERNAME_KEY = 'username'

const instance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000
})

function clearAllAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(USERNAME_KEY)
}

// 读 accessToken：优先 accessToken 字段，兼容 token 字段
function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || localStorage.getItem(TOKEN_KEY) || ''
}

function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

function saveAuthTokens(data) {
  // 兼容保留 token = accessToken
  if (data.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken)
    localStorage.setItem(TOKEN_KEY, data.accessToken)
  } else if (data.token) {
    localStorage.setItem(ACCESS_TOKEN_KEY, data.token)
    localStorage.setItem(TOKEN_KEY, data.token)
  }
  if (data.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refreshToken)
  }
  if (data.userId !== undefined && data.userId !== null) {
    localStorage.setItem(USER_ID_KEY, String(data.userId))
  }
  if (data.username) {
    localStorage.setItem(USERNAME_KEY, data.username)
  }
}

// ===== 请求拦截：始终挂最新 accessToken =====
instance.interceptors.request.use(
  config => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// ===== 响应拦截：401 走 refresh 流程 =====
instance.interceptors.response.use(
  response => {
    // 兼容后端统一返回 { success, message, data/... } 结构
    return response.data
  },
  error => {
    const resp = error.response
    const originalRequest = error.config

    // 连不上服务器 / 超时 等无响应情况
    if (!resp) {
      return Promise.reject(error)
    }

    if (resp.status !== 401 || !originalRequest) {
      return Promise.reject(error)
    }

    // 已经用新 Token 重放过仍然 401 → refresh 也失效了，回登录页
    if (originalRequest._retried) {
      clearAllAuth()
      router.push('/login')
      return Promise.reject(error)
    }

    if (!getRefreshToken()) {
      clearAllAuth()
      router.push('/login')
      return Promise.reject(error)
    }

    originalRequest._retried = true
    // auth.js 内部单飞：并发请求共享同一次 refresh，不会互相踢掉
    return refreshAccessToken()
      .then(newToken => {
        originalRequest.headers = {
          ...(originalRequest.headers || {}),
          Authorization: `Bearer ${newToken}`
        }
        return instance.request(originalRequest)
      })
      .catch(refreshErr => {
        clearAllAuth()
        router.push('/login')
        return Promise.reject(refreshErr)
      })
  }
)

export { instance as default, saveAuthTokens, clearAllAuth, getAccessToken, getRefreshToken }
