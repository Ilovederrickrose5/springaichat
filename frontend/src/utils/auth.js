import axios from 'axios'
import BASE_URL from '../api/config'
import { saveAuthTokens, getRefreshToken, getAccessToken } from './axios'

const REFRESH_URL = `${BASE_URL}/auth/refresh`

// 单飞：并发 401（含 SSE）共用同一次 refresh，避免 Token Rotation 互相踢掉
let refreshPromise = null

async function doRefresh() {
  const refreshTokenValue = getRefreshToken()
  if (!refreshTokenValue) {
    throw new Error('no refresh token')
  }

  const res = await axios.post(
    REFRESH_URL,
    { refreshToken: refreshTokenValue },
    { timeout: 15000 }
  )
  const body = res.data
  if (body && body.success) {
    // 保存新的 access + refresh（旧 refresh 立即失效）
    saveAuthTokens(body)
    return getAccessToken()
  }
  throw new Error(body?.message || 'refresh failed')
}

/**
 * 用 refreshToken 换取新的双 Token（Token Rotation）。
 * 使用裸 axios 直接请求，不走 axios 实例的拦截器，避免 401 → refresh → 401 死循环。
 *
 * @returns {Promise<string>} 新的 accessToken
 * @throws refreshToken 不存在 / 接口失败 / 后端返回 success=false 时抛错
 */
export function refreshToken() {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}
