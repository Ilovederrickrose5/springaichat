// API 配置：默认走相对路径 /api，开发环境由 Vite 代理转发，生产环境由同源网关转发。
// 需要跨域部署时用 VITE_API_BASE_URL 覆盖。
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export default BASE_URL
