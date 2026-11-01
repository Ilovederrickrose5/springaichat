<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-brand">
        <header class="brand-top">
          <BrandMark tone="light" size="sm" />
          <div class="brand-word">
            <strong>企智通</strong>
            <span>Enterprise RAG</span>
          </div>
        </header>

        <div class="brand-copy">
          <p class="eyebrow">Enterprise knowledge intelligence</p>
          <h1>让企业的知识，<br />有问必答。</h1>
          <p class="lede">
            把散落在制度、流程与文档里的经验，变成团队随时可用的答案。
            检索增强、上下文记忆、多会话隔离，一次问清。
          </p>

          <ul class="brand-points">
            <li>
              <span class="point-dot"></span>
              <div>
                <strong>答案来自企业资料</strong>
                <span>知识库检索增强，不凭空编造</span>
              </div>
            </li>
            <li>
              <span class="point-dot"></span>
              <div>
                <strong>记得每一次追问</strong>
                <span>上下文持续记忆，不用重复背景</span>
              </div>
            </li>
            <li>
              <span class="point-dot"></span>
              <div>
                <strong>会话彼此隔离</strong>
                <span>每个人只看见自己的对话记录</span>
              </div>
            </li>
          </ul>
        </div>

        <footer class="brand-foot">
          <span>企智通 · v1.0</span>
          <span>内部使用</span>
        </footer>
      </section>

      <main class="auth-panel">
        <div class="auth-card">
          <div class="mobile-brand">
            <BrandMark size="sm" />
            <strong>企智通</strong>
          </div>

          <header class="auth-heading">
            <h2>欢迎回来</h2>
            <p>登录后继续你的知识对话</p>
          </header>

          <form class="auth-form" novalidate @submit.prevent="handleLogin">
            <div class="field">
              <label for="login-username">用户名</label>
              <input
                id="login-username"
                v-model="username"
                class="field-input"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
                autofocus
                :disabled="loading"
              />
            </div>

            <div class="field">
              <label for="login-password">密码</label>
              <div class="field-control">
                <input
                  id="login-password"
                  ref="passwordInput"
                  v-model="password"
                  class="field-input"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                  :disabled="loading"
                />
                <button
                  type="button"
                  class="field-action"
                  :aria-pressed="showPassword"
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <Transition name="fade">
              <p v-if="loginError" class="form-error" role="alert">
                <span class="error-dot"></span>
                {{ loginError }}
              </p>
            </Transition>

            <button class="submit-btn" type="submit" :disabled="loading">
              <span v-if="!loading" class="submit-label">登录</span>
              <span v-else class="submit-loading"><i class="spinner"></i>正在登录</span>
            </button>

            <p class="form-hint">按下 Enter 快速登录</p>
          </form>

          <div class="auth-switch">
            还没有账号？
            <button type="button" class="link-btn" @click="openRegister">创建账号</button>
          </div>
          <p class="auth-note">首次使用请先创建账号</p>
        </div>
      </main>
    </div>

    <Transition name="modal">
      <div v-if="showRegister" class="modal-overlay" @click.self="closeRegister">
        <div class="register-card" role="dialog" aria-modal="true" aria-labelledby="register-title">
          <header class="modal-head">
            <div>
              <h3 id="register-title">创建账号</h3>
              <p>注册后即可开始企业知识问答</p>
            </div>
            <button type="button" class="icon-btn close-btn" aria-label="关闭" @click="closeRegister">
              &times;
            </button>
          </header>

          <div class="modal-body">
            <div class="field">
              <label for="register-username">用户名</label>
              <input
                id="register-username"
                ref="registerNameInput"
                v-model="registerUsername"
                class="field-input"
                type="text"
                placeholder="请输入用户名"
                autocomplete="username"
                :disabled="registerLoading"
              />
              <span class="field-hint">3-50 个字符，用于登录识别</span>
            </div>

            <div class="field">
              <label for="register-password">密码</label>
              <input
                id="register-password"
                v-model="registerPassword"
                class="field-input"
                type="password"
                placeholder="请输入密码"
                autocomplete="new-password"
                :disabled="registerLoading"
              />
              <span class="field-hint">至少 6 个字符</span>
            </div>

            <div class="field">
              <label for="register-confirm">确认密码</label>
              <input
                id="register-confirm"
                v-model="registerConfirmPassword"
                class="field-input"
                type="password"
                placeholder="请再次输入密码"
                autocomplete="new-password"
                :disabled="registerLoading"
                @keyup.enter="handleRegister"
              />
            </div>

            <Transition name="fade">
              <p v-if="registerError" class="form-error" role="alert">
                <span class="error-dot"></span>
                {{ registerError }}
              </p>
            </Transition>
          </div>

          <footer class="modal-actions">
            <button type="button" class="btn-ghost" :disabled="registerLoading" @click="closeRegister">
              取消
            </button>
            <button type="button" class="btn-solid" :disabled="registerLoading" @click="handleRegister">
              <span v-if="!registerLoading">创建账号</span>
              <span v-else class="submit-loading"><i class="spinner"></i>创建中</span>
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import axios, { saveAuthTokens } from '../utils/axios'
import router from '../router'
import BrandMark from '../components/BrandMark.vue'

// 后端校验失败返回 { message, errors: { field: message } }，优先展示字段级提示
function extractErrorMessage(error, fallback) {
  const body = error?.response?.data
  if (body?.errors && typeof body.errors === 'object') {
    const first = Object.values(body.errors)[0]
    if (first) return first
  }
  return body?.message || fallback
}

const username = ref('')
const password = ref('')
const loading = ref(false)
const showPassword = ref(false)
const loginError = ref('')
const passwordInput = ref(null)

const showRegister = ref(false)
const registerUsername = ref('')
const registerPassword = ref('')
const registerConfirmPassword = ref('')
const registerLoading = ref(false)
const registerError = ref('')
const registerNameInput = ref(null)

watch([username, password], () => {
  if (loginError.value) loginError.value = ''
})

watch([registerUsername, registerPassword, registerConfirmPassword], () => {
  if (registerError.value) registerError.value = ''
})

const openRegister = () => {
  showRegister.value = true
  registerError.value = ''
  nextTick(() => registerNameInput.value?.focus())
}

const closeRegister = () => {
  if (registerLoading.value) return
  showRegister.value = false
}

const handleLogin = async () => {
  if (loading.value) return

  const name = username.value.trim()
  if (!name || !password.value) {
    loginError.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  loginError.value = ''

  try {
    const response = await axios.post('/auth/login', {
      username: name,
      password: password.value
    })

    if (response.success) {
      saveAuthTokens(response)
      await router.push('/chat')
    } else {
      loginError.value = response.message || '登录失败'
    }
  } catch (error) {
    loginError.value = extractErrorMessage(error, '登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (registerLoading.value) return

  const name = registerUsername.value.trim()
  if (!name || !registerPassword.value) {
    registerError.value = '请输入用户名和密码'
    return
  }
  if (name.length < 3 || name.length > 50) {
    registerError.value = '用户名长度需为 3-50 个字符'
    return
  }
  if (registerPassword.value.length < 6) {
    registerError.value = '密码至少 6 个字符'
    return
  }
  if (registerPassword.value !== registerConfirmPassword.value) {
    registerError.value = '两次输入的密码不一致'
    return
  }

  registerLoading.value = true
  registerError.value = ''

  try {
    const response = await axios.post('/auth/register', {
      username: name,
      password: registerPassword.value
    })

    if (response.success) {
      ElMessage.success('注册成功，请登录')
      showRegister.value = false
      registerUsername.value = ''
      registerPassword.value = ''
      registerConfirmPassword.value = ''
      username.value = name
      password.value = ''
      nextTick(() => passwordInput.value?.focus())
    } else {
      registerError.value = response.message || '注册失败'
    }
  } catch (error) {
    registerError.value = extractErrorMessage(error, '注册失败，请检查网络连接')
  } finally {
    registerLoading.value = false
  }
}

const onKeydown = (event) => {
  if (event.key === 'Escape' && showRegister.value) closeRegister()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.auth-page {
  min-height: 100dvh;
  height: 100dvh;
  overflow-y: auto;
  display: grid;
  place-items: center;
  padding: 28px;
}

.auth-shell {
  width: 100%;
  max-width: 1080px;
  min-height: min(688px, calc(100dvh - 56px));
  display: grid;
  grid-template-columns: 1.06fr 1fr;
  background: var(--surface);
  border: 1px solid rgba(27, 30, 36, 0.06);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  animation: fade-up 560ms var(--ease-out) both;
}

/* ---------- 品牌侧 ---------- */
.auth-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 40px 44px 30px;
  color: #f4f4f2;
  overflow: hidden;
  background:
    radial-gradient(560px 400px at 16% 4%, rgba(93, 128, 158, 0.36), transparent 64%),
    radial-gradient(420px 340px at 96% 100%, rgba(181, 131, 90, 0.22), transparent 62%),
    linear-gradient(160deg, #272c34 0%, #1a1d23 58%, #14161b 100%);
}

.auth-brand::after {
  content: "";
  position: absolute;
  right: -210px;
  bottom: -230px;
  width: 540px;
  height: 540px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow:
    0 0 0 64px rgba(255, 255, 255, 0.024),
    0 0 0 150px rgba(255, 255, 255, 0.016);
  pointer-events: none;
}

.brand-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-word {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-word strong {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.brand-word span {
  margin-top: 2px;
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(244, 244, 242, 0.52);
}

.brand-copy {
  position: relative;
  z-index: 1;
  margin: auto 0;
  padding: 40px 0 34px;
}

.eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(244, 244, 242, 0.54);
}

.eyebrow::before {
  content: "";
  width: 26px;
  height: 1px;
  background: rgba(244, 244, 242, 0.32);
}

.brand-copy h1 {
  margin: 20px 0 16px;
  font-size: clamp(30px, 3.3vw, 42px);
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.01em;
  color: #f8f7f4;
}

.lede {
  max-width: 36ch;
  font-size: 13.5px;
  line-height: 1.8;
  color: rgba(244, 244, 242, 0.66);
}

.brand-points {
  display: grid;
  gap: 15px;
  margin-top: 34px;
  list-style: none;
}

.brand-points li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.point-dot {
  width: 7px;
  height: 7px;
  margin-top: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: rgba(196, 154, 112, 0.9);
  box-shadow: 0 0 0 4px rgba(196, 154, 112, 0.1);
}

.brand-points div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-points strong {
  font-size: 13px;
  font-weight: 500;
  color: rgba(248, 247, 244, 0.9);
}

.brand-points span {
  font-size: 12px;
  color: rgba(244, 244, 242, 0.54);
}

.brand-foot {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 22px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 11px;
  letter-spacing: 0.06em;
  color: rgba(244, 244, 242, 0.48);
}

/* ---------- 表单侧 ---------- */
.auth-panel {
  display: grid;
  place-items: center;
  padding: 48px 44px;
  background: linear-gradient(180deg, #fdfcfa 0%, #f7f6f2 100%);
}

.auth-card {
  width: 100%;
  max-width: 352px;
  animation: fade-up 640ms 90ms var(--ease-out) both;
}

.mobile-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
}

.mobile-brand strong {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--ink-900);
}

.auth-heading h2 {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.auth-heading p {
  margin-top: 8px;
  font-size: 13px;
  color: var(--ink-500);
}

.auth-form {
  margin-top: 30px;
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ink-700);
}

.field-control {
  position: relative;
}

.field-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  font-size: 14px;
  color: var(--ink-900);
  transition:
    border-color var(--dur) var(--ease-soft),
    box-shadow var(--dur) var(--ease-soft),
    background-color var(--dur) var(--ease-soft);
}

.field-input::placeholder {
  color: var(--ink-300);
}

.field-input:hover:not(:disabled) {
  border-color: rgba(27, 30, 36, 0.22);
}

.field-input:focus,
.field-input:focus-visible {
  outline: none;
  border-color: rgba(63, 91, 116, 0.55);
  background: #fff;
  box-shadow: 0 0 0 4px var(--accent-wash);
}

.field-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.field-control .field-input {
  padding-right: 62px;
}

.field-action {
  position: absolute;
  top: 50%;
  right: 7px;
  transform: translateY(-50%);
  padding: 6px 9px;
  border-radius: 7px;
  font-size: 12px;
  color: var(--ink-400);
  transition:
    color var(--dur) var(--ease-soft),
    background-color var(--dur) var(--ease-soft);
}

.field-action:hover {
  color: var(--ink-800);
  background: rgba(27, 30, 36, 0.05);
}

.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--ink-400);
}

.form-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 9px 12px;
  border: 1px solid rgba(176, 74, 67, 0.16);
  border-radius: 10px;
  background: var(--danger-soft);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--danger);
}

.error-dot {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--danger);
}

.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(180deg, #2b2f38 0%, #1b1e24 100%);
  color: #faf9f7;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.12em;
  box-shadow:
    0 8px 20px -10px rgba(27, 30, 36, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition:
    transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out),
    opacity var(--dur) var(--ease-soft);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow:
    0 12px 26px -10px rgba(27, 30, 36, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 4px 12px -8px rgba(27, 30, 36, 0.5);
}

.submit-btn:disabled {
  opacity: 0.78;
  cursor: default;
}

.submit-label {
  display: inline-block;
}

.submit-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  letter-spacing: 0.04em;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 800ms linear infinite;
}

.form-hint {
  margin-top: 13px;
  text-align: center;
  font-size: 11.5px;
  color: var(--ink-400);
}

.auth-switch {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
  text-align: center;
  font-size: 13px;
  color: var(--ink-500);
}

.link-btn {
  margin-left: 4px;
  padding: 2px 2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--accent);
  border-bottom: 1px solid transparent;
  transition:
    color var(--dur) var(--ease-soft),
    border-color var(--dur) var(--ease-soft);
}

.link-btn:hover {
  color: var(--accent-deep);
  border-bottom-color: rgba(63, 91, 116, 0.4);
}

.auth-note {
  margin-top: 10px;
  text-align: center;
  font-size: 11.5px;
  color: var(--ink-400);
}

/* ---------- 注册弹窗 ---------- */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(20, 22, 27, 0.36);
  backdrop-filter: blur(8px);
}

.register-card {
  width: 100%;
  max-width: 404px;
  padding: 26px 28px 24px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.modal-head h3 {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.modal-head p {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--ink-400);
}

.close-btn {
  margin-top: -4px;
  margin-right: -6px;
  font-size: 20px;
  line-height: 1;
  color: var(--ink-400);
}

.modal-body {
  margin-top: 22px;
}

.modal-body .field:last-of-type {
  margin-bottom: 4px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.btn-ghost,
.btn-solid {
  height: 42px;
  padding: 0 20px;
  border-radius: 11px;
  font-size: 13.5px;
  font-weight: 500;
  transition:
    background-color var(--dur) var(--ease-soft),
    border-color var(--dur) var(--ease-soft),
    transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out),
    opacity var(--dur) var(--ease-soft);
}

.btn-ghost {
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: var(--ink-700);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--surface-soft);
  border-color: rgba(27, 30, 36, 0.22);
}

.btn-solid {
  background: linear-gradient(180deg, #2b2f38, #1b1e24);
  color: #faf9f7;
  box-shadow:
    0 6px 16px -10px rgba(27, 30, 36, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

.btn-solid:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow:
    0 10px 22px -10px rgba(27, 30, 36, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.btn-solid:disabled,
.btn-ghost:disabled {
  opacity: 0.7;
  cursor: default;
}

/* ---------- 过渡 ---------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms var(--ease-soft);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 240ms var(--ease-soft);
}

.modal-enter-active .register-card,
.modal-leave-active .register-card {
  transition:
    opacity 280ms var(--ease-out),
    transform 320ms var(--ease-out);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .register-card,
.modal-leave-to .register-card {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .auth-page {
    padding: 20px;
  }

  .auth-shell {
    max-width: 440px;
    min-height: calc(100dvh - 40px);
    grid-template-columns: 1fr;
    border-radius: 26px;
  }

  .auth-brand {
    display: none;
  }

  .auth-panel {
    padding: 44px 32px;
  }

  .mobile-brand {
    display: flex;
  }

  .auth-card {
    animation: none;
  }
}

@media (max-width: 420px) {
  .auth-page {
    padding: 12px;
  }

  .auth-shell {
    min-height: calc(100dvh - 24px);
    border-radius: 22px;
  }

  .auth-panel {
    padding: 32px 22px;
  }

  .auth-heading h2 {
    font-size: 24px;
  }

  .register-card {
    padding: 22px 20px 20px;
  }
}
</style>
