import axios from 'axios'
import { saveAuthTokens, getRefreshToken, getAccessToken } from './axios'

const REFRESH_URL = 'http://localhost:8080/api/auth/refresh'

/**
 * 用 refreshToken 换取新的双 Token（Token Rotation）。
 * 使用裸 axios 直接请求，不走 axios 实例的拦截器，避免 401 → refresh → 401 死循环。
 *
 * @returns {Promise<string>} 新的 accessToken
 * @throws refreshToken 不存在 / 接口失败 / 后端返回 success=false 时抛错
 */
export async function refreshToken() {
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
