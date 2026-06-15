import axios from 'axios'
import router from '../router'

const instance = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000
})

// 请求拦截器：添加 token
instance.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：处理 token 过期
instance.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default instance