import axios from 'axios'
import router from '../router'

// ===== 本地存储 key =====
const TOKEN_KEY = 'token'
const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_ID_KEY = 'userId'
const USERNAME_KEY = 'username'

const instance = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000
})

// ===== 并发 refresh 控制：同一时刻只允许一次 refresh 请求，其余等待 =====
let isRefreshing = false
let pendingRequests = [] // { resolve, reject, originalConfig }

function runPendingRequests(error, newToken) {
  pendingRequests.forEach(p => {
    if (error) {
      p.reject(error)
    } else {
      // 用新的 accessToken 重新执行原请求
      const cfg = { ...p.originalConfig }
      cfg.headers = { ...(cfg.headers || {}), Authorization: `Bearer ${newToken}` }
      instance.request(cfg).then(p.resolve).catch(p.reject)
    }
  })
  pendingRequests = []
}

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
    // 兼容我们后端统一返回 { success, message, data/... } 结构
    // 历史约定：axios 响应 data 直接 return response.data 给调用方
    return response.data
  },
  error => {
    const resp = error.response
    const originalRequest = error.config

    // 连不上服务器 / 超时 等无响应情况
    if (!resp) {
      return Promise.reject(error)
    }

    // 401 —— 尝试用 refresh 换新 accessToken
    if (resp.status === 401) {
      // 场景 A：当前 401 这个请求本身就是 refresh 接口 → refresh 也挂了，只能去登录
      const urlPath = (originalRequest.url || '').replace(/^http:\/\/[^/]+\/api/, '')
      if (urlPath === '/auth/refresh' || urlPath.endsWith('/api/auth/refresh')) {
        clearAllAuth()
        router.push('/login')
        return Promise.reject(error)
      }

      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        // 没有 refreshToken，直接退登录
        clearAllAuth()
        router.push('/login')
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // 正在刷新中，挂到等待队列
        return new Promise((resolve, reject) => {
          pendingRequests.push({ resolve, reject, originalConfig: originalRequest })
        })
      }

      isRefreshing = true
      // 用一个「临时裸 axios」直接发起 refresh，不要再走我们的拦截器（防死循环）
      return axios.post('http://localhost:8080/api/auth/refresh', { refreshToken }, {
        timeout: 15000
      }).then(res => {
        const body = res.data
        if (body && body.success) {
          saveAuthTokens(body)
          const newToken = getAccessToken()
          // 把当前这个失败的请求补上新的 Token 再发一次
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          runPendingRequests(null, newToken)
          return instance.request(originalRequest)
        } else {
          // 后端告诉我们刷新失败
          runPendingRequests(error || new Error('refresh failed'), null)
          clearAllAuth()
          router.push('/login')
          return Promise.reject(error)
        }
      }).catch(refreshErr => {
        runPendingRequests(refreshErr, null)
        clearAllAuth()
        router.push('/login')
        return Promise.reject(refreshErr)
      }).finally(() => {
        isRefreshing = false
      })
    }

    // 非 401 按原错误返回
    return Promise.reject(error)
  }
)

export { instance as default, saveAuthTokens, clearAllAuth, getAccessToken, getRefreshToken }
