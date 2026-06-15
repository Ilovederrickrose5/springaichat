<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>智能聊天助手</h1>
        <p>基于 Spring AI 构建</p>
      </div>

      <div class="login-form">
        <div class="form-item">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="form-input"
          />
        </div>

        <div class="form-item">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="form-input"
          />
        </div>

        <button
          type="button"
          class="login-btn"
          :disabled="loading"
          @click="handleLogin"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </button>

        <div class="register-link">
          <span>还没有账号？</span>
          <a href="javascript:void(0)" @click="showRegister = true">立即注册</a>
        </div>
      </div>
    </div>

    <!-- 注册弹窗 -->
    <div v-if="showRegister" class="modal-overlay">
      <div class="modal-content">
        <h3>用户注册</h3>
        <div class="form-item">
          <label>用户名</label>
          <input
            v-model="registerUsername"
            type="text"
            placeholder="请输入用户名"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label>密码</label>
          <input
            v-model="registerPassword"
            type="password"
            placeholder="请输入密码"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label>确认密码</label>
          <input
            v-model="registerConfirmPassword"
            type="password"
            placeholder="请再次输入密码"
            class="form-input"
          />
        </div>
        <div class="modal-footer">
          <button type="button" @click="showRegister = false">取消</button>
          <button
            type="button"
            class="primary"
            :disabled="registerLoading"
            @click="handleRegister"
          >
            {{ registerLoading ? '注册中...' : '注 册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import axios from '../utils/axios'
import router from '../router'

const username = ref('')
const password = ref('')
const loading = ref(false)
const showRegister = ref(false)

const registerUsername = ref('')
const registerPassword = ref('')
const registerConfirmPassword = ref('')
const registerLoading = ref(false)

const handleLogin = async () => {
  if (!username.value || !password.value) {
    alert('请输入用户名和密码')
    return
  }

  loading.value = true

  try {
    const response = await axios.post('/auth/login', {
      username: username.value,
      password: password.value
    })

    if (response.success) {
      localStorage.setItem('token', response.token)
      localStorage.setItem('userId', response.userId)
      localStorage.setItem('username', response.username)
      await router.push('/chat')
    } else {
      alert(response.message || '登录失败')
    }
  } catch (error) {
    alert(error.response?.data?.message || '登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerUsername.value || !registerPassword.value) {
    alert('请输入用户名和密码')
    return
  }

  if (registerPassword.value !== registerConfirmPassword.value) {
    alert('两次输入的密码不一致')
    return
  }

  registerLoading.value = true

  try {
    const response = await axios.post('/auth/register', {
      username: registerUsername.value,
      password: registerPassword.value
    })

    if (response.success) {
      alert('注册成功，请登录')
      showRegister.value = false
      registerUsername.value = ''
      registerPassword.value = ''
      registerConfirmPassword.value = ''
    } else {
      alert(response.message || '注册失败')
    }
  } catch (error) {
    alert('注册失败，请检查网络连接')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-box {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.login-header p {
  color: #999;
  font-size: 14px;
}

.login-form {
  margin-top: 20px;
}

.form-item {
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #333;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: pointer;
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}

.register-link a {
  color: #667eea;
  text-decoration: none;
  margin-left: 4px;
}

.register-link a:hover {
  text-decoration: underline;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  background: white;
  padding: 30px;
  border-radius: 12px;
  width: 90%;
  max-width: 400px;
}

.modal-content h3 {
  margin-bottom: 20px;
  text-align: center;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.modal-footer button {
  padding: 8px 20px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  cursor: pointer;
}

.modal-footer button.primary {
  background: #667eea;
  color: white;
  border-color: #667eea;
}
</style>
