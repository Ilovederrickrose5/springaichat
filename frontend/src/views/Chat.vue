<template>
  <div class="chat-shell" @click="closeContextMenu">
    <ConversationList
      :conversations="conversations"
      :selected-conversation="selectedConversation"
      :is-collapsed="isCollapsed"
      :current-user="currentUser"
      @select="selectConversation"
      @create="createConversation"
      @delete="deleteConversation"
      @toggle-collapse="toggleCollapse"
      @logout="logout"
    />

    <Transition name="fade">
      <div
        v-if="!isCollapsed"
        class="sidebar-backdrop"
        aria-hidden="true"
        @click.stop="toggleCollapse"
      ></div>
    </Transition>

    <main class="chat-main">
      <div v-if="selectedConversation" class="chat-content">
        <header class="chat-header">
          <div class="header-main">
            <button
              v-show="isCollapsed"
              type="button"
              class="icon-btn expand-btn"
              title="展开会话列表"
              aria-label="展开会话列表"
              @click.stop="toggleCollapse"
            >
              <el-icon><ArrowRight /></el-icon>
            </button>
            <div class="header-titles">
              <h1>{{ currentConversation?.title || '对话' }}</h1>
              <p>
                <span
                  v-if="isStreaming"
                  class="header-status-dot"
                  :class="{ 'is-paused': isPaused }"
                ></span>
                {{
                  isPaused
                    ? '已暂停生成，可继续或切换会话'
                    : isStreaming
                      ? '正在思考并组织回答'
                      : '基于企业知识库 · 上下文已记忆'
                }}
              </p>
            </div>
          </div>

          <div class="header-actions">
            <Transition name="fade">
              <button
                v-if="selectedMessages.length > 0"
                type="button"
                class="batch-btn"
                @click="batchDeleteMessages"
              >
                <el-icon><Delete /></el-icon>
                删除 {{ selectedMessages.length }} 条
              </button>
            </Transition>
            <button
              type="button"
              class="icon-btn logout-btn"
              title="退出登录"
              aria-label="退出登录"
              @click.stop="logout"
            >
              <el-icon><SwitchButton /></el-icon>
            </button>
          </div>
        </header>

        <div
          ref="messageList"
          class="message-scroll"
          @scroll="handleScroll"
        >
          <div class="message-column">
            <div v-if="hasMoreMessages" class="load-earlier">
              <button
                type="button"
                class="text-btn"
                :disabled="loadingMore"
                @click.stop="loadEarlierMessages"
              >
                <span v-if="loadingMore" class="load-spinner"></span>
                {{ loadingMore ? '正在加载' : '加载更早消息' }}
              </button>
            </div>

            <div v-if="loading && messages.length === 0" class="skeleton-list" aria-hidden="true">
              <div
                v-for="n in 3"
                :key="n"
                class="skeleton-row"
                :class="{ user: n === 2 }"
              >
                <span class="skeleton-avatar"></span>
                <div class="skeleton-bubble">
                  <span class="skeleton-line" :class="n === 2 ? 'w-70' : 'w-80'"></span>
                  <span class="skeleton-line" :class="n === 2 ? 'w-40' : 'w-60'"></span>
                  <span v-if="n !== 2" class="skeleton-line w-35"></span>
                </div>
              </div>
            </div>

            <MessageItem
              v-for="msg in messages"
              :key="msg.id"
              :msg="msg"
              :selected="selectedMessages.includes(msg.id)"
              :formatted-time="formatTime(msg.createTime)"
              @contextmenu="handleContextMenu"
              @toggle-select="toggleMessageSelect"
              @toggle-pause="togglePauseMessage"
            />

            <div v-if="!loading && messages.length === 0" class="empty-chat">
              <div class="empty-visual">
                <span class="empty-halo"></span>
                <BrandMark size="lg" />
              </div>
              <h3>今天想了解什么？</h3>
              <p>企智通会先检索企业知识库，再结合对话上下文回答你的问题。</p>
              <div class="suggestions">
                <button
                  v-for="item in suggestions"
                  :key="item"
                  type="button"
                  class="suggestion-chip"
                  @click.stop="useSuggestion(item)"
                >
                  <span>{{ item }}</span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>

        <Transition name="pop">
          <button
            v-if="!isAtBottom"
            type="button"
            class="scroll-to-bottom"
            title="回到最新消息"
            aria-label="回到最新消息"
            @click.stop="scrollToBottomFromButton"
          >
            <el-icon><ArrowDown /></el-icon>
          </button>
        </Transition>

        <ContextMenu
          :visible="showContextMenu"
          :position="contextMenuPosition"
          :is-selected="selectedMessages.includes(contextMenuMessageId)"
          @select="toggleSelectFromMenu"
          @delete="deleteFromMenu"
        />

        <InputArea
          v-model="inputMessage"
          :disabled="isStreaming"
          :paused="isPaused"
          @send="sendMessageAction"
        />
      </div>

      <div v-else class="welcome-state">
        <div class="welcome-visual" aria-hidden="true">
          <span class="welcome-ring ring-a"></span>
          <span class="welcome-ring ring-b"></span>
          <div class="welcome-card card-back"></div>
          <div class="welcome-card card-front">
            <BrandMark size="md" />
            <span class="card-line w-70"></span>
            <span class="card-line w-45"></span>
          </div>
        </div>
        <h2>你好，我是企智通</h2>
        <p>企业知识的入口。新建一个会话，把需要查清的问题交给我。</p>
        <button type="button" class="welcome-cta" @click.stop="createConversation">
          <el-icon><Plus /></el-icon>
          开始新对话
        </button>
        <p class="welcome-note">你的会话仅自己可见</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios, { clearAllAuth, getAccessToken } from '@/utils/axios'
import { refreshToken } from '@/utils/auth'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { formatTime } from '@/utils/time'
import { useStreamChat } from '@/composables/useStreamChat'
import ConversationList from '@/components/ConversationList.vue'
import MessageItem from '@/components/MessageItem.vue'
import ContextMenu from '@/components/ContextMenu.vue'
import InputArea from '@/components/InputArea.vue'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()

// ===== 基础状态 =====
const conversations = ref([])
const selectedConversation = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messageList = ref(null)
const isAtBottom = ref(true)
const BOTTOM_THRESHOLD = 50
const isCollapsed = ref(window.innerWidth <= 820)
const currentUser = ref(localStorage.getItem('username') || '用户')
const selectedMessages = ref([])
const hasMoreMessages = ref(false)
const loadingMore = ref(false)
const PAGE_SIZE = 100
const showContextMenu = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuMessageId = ref(null)

const suggestions = [
  '公司资产盘点的流程是什么？',
  '设备出现故障时应该怎么报修？',
  '帮我总结一下知识库里的安全规范'
]

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

// ===== 滚动控制 =====
const scrollToBottom = () => {
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
    isAtBottom.value = true
  }
}
const handleScroll = () => {
  const el = messageList.value
  if (!el) return
  isAtBottom.value = (el.scrollHeight - el.scrollTop - el.clientHeight) <= BOTTOM_THRESHOLD
}
const scrollToBottomFromButton = () => scrollToBottom()

const currentConversation = computed(() =>
  conversations.value.find(c => c.id === selectedConversation.value)
)

const updateConversationInList = () => {
  const idx = conversations.value.findIndex(c => c.id === selectedConversation.value)
  if (idx !== -1) {
    conversations.value[idx].title = currentConversation.value?.title || '新对话'
    conversations.value[idx].updateTime = new Date().toISOString()
    const [removed] = conversations.value.splice(idx, 1)
    conversations.value.unshift(removed)
  }
}

// ===== 流式聊天（SSE + 打字机 + 暂停 + AbortController）=====
const {
  isStreaming,
  isPaused,
  abortCurrentStream,
  togglePauseMessage,
  sendMessageStream
} = useStreamChat({
  messages, selectedConversation, isAtBottom,
  scrollToBottom, updateConversationInList,
  getAccessToken, refreshToken, clearAllAuth,
  router, ElMessage
})

// ===== 右键菜单 =====
const MENU_WIDTH = 180
const MENU_HEIGHT = 112
const MENU_SAFE = 8

const handleContextMenu = (event, msg) => {
  event.preventDefault()
  contextMenuPosition.value = {
    x: Math.max(MENU_SAFE, Math.min(event.clientX, window.innerWidth - MENU_WIDTH - MENU_SAFE)),
    y: Math.max(MENU_SAFE, Math.min(event.clientY, window.innerHeight - MENU_HEIGHT - MENU_SAFE))
  }
  contextMenuMessageId.value = msg.id
  showContextMenu.value = true
}
const closeContextMenu = () => { showContextMenu.value = false }
// 本地临时消息（流式中的 user/assistant）尚未落库，没有可用的数据库 id
const isPersistedId = (id) => typeof id === 'number'
const toggleSelectFromMenu = () => {
  if (contextMenuMessageId.value !== null) {
    if (!isPersistedId(contextMenuMessageId.value)) {
      closeContextMenu()
      ElMessage.info('消息尚未保存，暂不能操作')
      return
    }
    const idx = selectedMessages.value.indexOf(contextMenuMessageId.value)
    if (idx > -1) selectedMessages.value.splice(idx, 1)
    else selectedMessages.value.push(contextMenuMessageId.value)
  }
  closeContextMenu()
}
const deleteFromMenu = () => {
  if (contextMenuMessageId.value) deleteMessage(contextMenuMessageId.value)
  closeContextMenu()
}

// ===== 会话管理 =====
const loadConversations = async () => {
  try {
    const response = await axios.get('/chat/conversations')
    if (response.success) {
      conversations.value = response.data || []
      // 默认进入最近更新的会话，避免空白页
      if (!selectedConversation.value && conversations.value.length > 0) {
        selectedConversation.value = conversations.value[0].id
      }
    }
  } catch (e) {
    ElMessage.error('加载会话列表失败')
  }
}

const fetchMessages = async (conversationId, beforeId = null) => {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) })
  if (beforeId !== null) params.set('beforeId', String(beforeId))
  const response = await axios.get(`/chat/conversations/${conversationId}/messages?${params}`)
  return response.success ? (response.data || []) : []
}

const loadMessages = async (conversationId) => {
  loading.value = true
  try {
    const list = await fetchMessages(conversationId)
    // 请求期间用户可能已切走，丢弃过期结果
    if (selectedConversation.value !== conversationId) return
    messages.value = list
    hasMoreMessages.value = list.length >= PAGE_SIZE
    nextTick(scrollToBottom)
  } catch (e) {
    ElMessage.error('加载消息失败')
  } finally {
    // 旧请求结束不能把新会话的加载态提前关掉
    if (selectedConversation.value === conversationId) loading.value = false
  }
}

const loadEarlierMessages = async () => {
  const firstId = messages.value[0]?.id
  if (loadingMore.value || !hasMoreMessages.value || firstId === undefined) return
  if (typeof firstId !== 'number') return

  loadingMore.value = true
  const el = messageList.value
  const previousHeight = el ? el.scrollHeight : 0

  try {
    const older = await fetchMessages(selectedConversation.value, firstId)
    messages.value = [...older, ...messages.value]
    hasMoreMessages.value = older.length >= PAGE_SIZE
    await nextTick()
    // 保持当前位置：新增内容到顶部后补偿滚动距离
    if (el) el.scrollTop = el.scrollHeight - previousHeight
  } catch (e) {
    ElMessage.error('加载更早消息失败')
  } finally {
    loadingMore.value = false
  }
}
const createConversation = async () => {
  abortCurrentStream()
  selectedMessages.value = []
  try {
    const response = await axios.post('/chat/conversations')
    if (response.success) {
      conversations.value.unshift(response.data)
      selectedConversation.value = response.data.id
    }
  } catch (e) {
    ElMessage.error('创建会话失败')
  }
}
const selectConversation = (conversationId) => {
  if (conversationId === selectedConversation.value) return
  abortCurrentStream()
  selectedMessages.value = []
  closeContextMenu()
  hasMoreMessages.value = false
  messages.value = []
  selectedConversation.value = conversationId
}
const deleteConversation = async (conversationId) => {
  ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      const response = await axios.delete(`/chat/conversations/${conversationId}`)
      if (response.success) {
        conversations.value = conversations.value.filter(c => c.id !== conversationId)
        if (selectedConversation.value === conversationId) {
          abortCurrentStream()
          selectedConversation.value = conversations.value.length > 0 ? conversations.value[0].id : null
          messages.value = []
          selectedMessages.value = []
          hasMoreMessages.value = false
        }
        ElMessage.success('删除成功')
      }
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// ===== 消息管理 =====
const toggleMessageSelect = (messageId, checked) => {
  if (checked) {
    if (!selectedMessages.value.includes(messageId)) selectedMessages.value.push(messageId)
  } else {
    selectedMessages.value = selectedMessages.value.filter(id => id !== messageId)
  }
}
const deleteMessage = async (messageId) => {
  if (!isPersistedId(messageId)) {
    ElMessage.info('消息尚未保存，暂不能删除')
    return
  }
  ElMessageBox.confirm('确定要删除这条消息吗？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      const response = await axios.delete(`/chat/messages/${messageId}`)
      if (response.success) {
        messages.value = messages.value.filter(m => m.id !== messageId)
        selectedMessages.value = selectedMessages.value.filter(id => id !== messageId)
        ElMessage.success('消息删除成功')
      }
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}
const batchDeleteMessages = async () => {
  if (selectedMessages.value.length === 0) return
  ElMessageBox.confirm(`确定要删除选中的 ${selectedMessages.value.length} 条消息吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      const response = await axios.post('/chat/messages/batch-delete', {
        messageIds: selectedMessages.value
      })
      if (response.success) {
        const count = selectedMessages.value.length
        messages.value = messages.value.filter(m => !selectedMessages.value.includes(m.id))
        selectedMessages.value = []
        ElMessage.success(`成功删除 ${count} 条消息`)
      }
    } catch (error) {
      ElMessage.error('批量删除失败')
    }
  }).catch(() => {})
}

const sendMessageAction = async () => {
  const content = inputMessage.value.trim()
  if (!content || !selectedConversation.value) return

  // 新会话第一条消息：先本地更新标题，避免等后端异步落库后列表仍显示「新对话」
  if (messages.value.length === 0) {
    const conv = currentConversation.value
    if (conv && (!conv.title || conv.title === '新对话')) {
      conv.title = content.length > 30 ? `${content.slice(0, 30)}...` : content
    }
  }

  inputMessage.value = ''
  await sendMessageStream(content)
}

const useSuggestion = (text) => {
  if (isStreaming.value) return
  inputMessage.value = text
  sendMessageAction()
}

// ===== 登出 =====
const logout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'info'
  }).then(async () => {
    abortCurrentStream()
    try {
      await axios.post('/auth/logout')
    } catch (e) {
      console.warn('logout api call failed, continue to clear local state', e)
    }
    clearAllAuth()
    currentUser.value = '用户'
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

// 会话切换时加载消息
watch(selectedConversation, (newId) => {
  if (newId) loadMessages(newId)
})

const onGlobalKeydown = (event) => {
  if (event.key === 'Escape') closeContextMenu()
}

onMounted(() => {
  loadConversations()
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style scoped>
.chat-shell {
  position: relative;
  display: flex;
  height: 100dvh;
  padding: 14px;
  overflow: hidden;
}

/* ---------- 主面板 ---------- */
.chat-main {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(27, 30, 36, 0.06);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, #fdfcfa 0%, #f9f8f4 100%);
  box-shadow: var(--shadow-sm);
}

.sidebar-backdrop {
  display: none;
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* ---------- 顶栏 ---------- */
.chat-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 17px 24px 15px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(253, 252, 250, 0.78));
  backdrop-filter: blur(10px);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.expand-btn {
  width: 32px;
  height: 32px;
  margin-left: -2px;
  flex-shrink: 0;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
  animation: fade-up 240ms var(--ease-out) both;
}

.expand-btn:hover {
  border-color: var(--line-strong);
  background: var(--surface-soft);
}

.header-titles {
  min-width: 0;
}

.header-titles h1 {
  max-width: min(52vw, 560px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.005em;
  color: var(--ink-900);
}

.header-titles p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-400);
}

.header-status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse-dot 1.6s var(--ease-soft) infinite;
}

.header-status-dot.is-paused {
  background: var(--warm);
  animation: none;
  opacity: 0.85;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(176, 74, 67, 0.14);
  border-radius: 9px;
  background: var(--danger-soft);
  font-size: 12px;
  color: var(--danger);
  transition:
    background-color var(--dur) var(--ease-soft),
    border-color var(--dur) var(--ease-soft);
}

.batch-btn:hover {
  background: #f7e9e7;
  border-color: rgba(176, 74, 67, 0.24);
}

/* ---------- 消息流 ---------- */
.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 26px 24px 8px;
}

.message-column {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 820px;
  min-height: 100%;
  margin: 0 auto;
}

.load-earlier {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}

.load-spinner {
  width: 11px;
  height: 11px;
  border: 1.5px solid var(--line-strong);
  border-top-color: var(--ink-500);
  border-radius: 50%;
  animation: spin 800ms linear infinite;
}

/* 骨架屏 */
.skeleton-list {
  padding-top: 4px;
}

.skeleton-row {
  display: flex;
  gap: 12px;
  margin-bottom: 26px;
}

.skeleton-row.user {
  flex-direction: row-reverse;
}

.skeleton-avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 9px;
  background: rgba(27, 30, 36, 0.06);
}

.skeleton-bubble {
  display: flex;
  flex-direction: column;
  gap: 9px;
  width: min(420px, 58%);
  padding: 15px 16px;
  border-radius: 6px 18px 18px 18px;
  background: rgba(27, 30, 36, 0.028);
}

.skeleton-row.user .skeleton-bubble {
  align-items: flex-end;
  border-radius: 18px 6px 18px 18px;
  background: rgba(27, 30, 36, 0.05);
}

.skeleton-line,
.card-line {
  display: block;
  height: 9px;
  border-radius: var(--radius-pill);
  background: linear-gradient(
    90deg,
    rgba(27, 30, 36, 0.045) 25%,
    rgba(27, 30, 36, 0.085) 50%,
    rgba(27, 30, 36, 0.045) 75%
  );
  background-size: 240px 100%;
  animation: shimmer 1.6s linear infinite;
}

.w-80 { width: 80%; }
.w-70 { width: 70%; }
.w-60 { width: 60%; }
.w-45 { width: 45%; }
.w-40 { width: 40%; }
.w-35 { width: 35%; }

/* 空对话 */
.empty-chat {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 12px 48px;
  text-align: center;
  animation: fade-up 520ms var(--ease-out) both;
}

.empty-visual {
  position: relative;
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  margin-bottom: 20px;
}

.empty-visual::before {
  content: "";
  position: absolute;
  inset: -11px;
  border: 1px solid var(--line);
  border-radius: 50%;
}

.empty-halo {
  position: absolute;
  inset: -30px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(63, 91, 116, 0.12), transparent 68%);
  filter: blur(2px);
}

.empty-chat h3 {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.empty-chat > p {
  max-width: 440px;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-500);
  text-wrap: balance;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 580px;
  margin-top: 24px;
}

.suggestion-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.82);
  font-size: 12.5px;
  color: var(--ink-700);
  box-shadow: var(--shadow-xs);
  transition:
    transform var(--dur) var(--ease-out),
    border-color var(--dur) var(--ease-soft),
    box-shadow var(--dur) var(--ease-soft),
    color var(--dur) var(--ease-soft);
}

.suggestion-chip :deep(.el-icon) {
  font-size: 11px;
  color: var(--ink-300);
  transition:
    transform var(--dur) var(--ease-out),
    color var(--dur) var(--ease-soft);
}

.suggestion-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(63, 91, 116, 0.28);
  color: var(--ink-900);
  box-shadow: var(--shadow-sm);
}

.suggestion-chip:hover :deep(.el-icon) {
  transform: translateX(2px);
  color: var(--accent);
}

/* 回到最新 */
.scroll-to-bottom {
  position: absolute;
  right: 30px;
  bottom: 132px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  color: var(--ink-500);
  box-shadow: var(--shadow-md);
  transition:
    transform var(--dur) var(--ease-out),
    color var(--dur) var(--ease-soft),
    box-shadow var(--dur) var(--ease-out);
}

.scroll-to-bottom:hover {
  transform: translateY(-2px);
  color: var(--ink-900);
  box-shadow: var(--shadow-lg);
}

.pop-enter-active,
.pop-leave-active {
  transition:
    opacity 200ms var(--ease-soft),
    transform 260ms var(--ease-out);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.94);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms var(--ease-soft);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------- 未选择会话 ---------- */
.welcome-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
  animation: fade-up 560ms var(--ease-out) both;
}

.welcome-visual {
  position: relative;
  width: 210px;
  height: 150px;
  margin-bottom: 34px;
}

.welcome-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid var(--line);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.ring-a {
  width: 148px;
  height: 148px;
}

.ring-b {
  width: 196px;
  height: 196px;
  border-style: dashed;
  opacity: 0.6;
}

.welcome-card {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 150px;
  height: 92px;
  border-radius: 15px;
}

.card-back {
  border: 1px solid var(--line);
  background: var(--surface-tint);
  transform: translate(-56%, -54%) rotate(-7deg);
}

.card-front {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  border: 1px solid rgba(27, 30, 36, 0.07);
  background: linear-gradient(160deg, #ffffff, #fbfaf7);
  box-shadow: var(--shadow-md);
  transform: translate(-44%, -46%) rotate(2deg);
}

.card-front .card-line {
  height: 6px;
  animation: none;
  background: var(--surface-tint);
}

.welcome-state h2 {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.welcome-state > p {
  max-width: 390px;
  margin-top: 9px;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ink-500);
  text-wrap: balance;
}

.welcome-cta {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 44px;
  margin-top: 26px;
  padding: 0 21px;
  border-radius: 12px;
  background: linear-gradient(180deg, #2b2f38, #1b1e24);
  color: #f7f6f3;
  font-size: 13.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  box-shadow:
    0 10px 24px -14px rgba(27, 30, 36, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition:
    transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out);
}

.welcome-cta:hover {
  transform: translateY(-1px);
  box-shadow:
    0 14px 28px -14px rgba(27, 30, 36, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.welcome-cta:active {
  transform: translateY(0);
}

.welcome-note {
  margin-top: 14px;
  font-size: 11px;
  color: var(--ink-400);
}

/* ---------- 响应式 ---------- */
@media (max-width: 820px) {
  .chat-shell {
    padding: 10px;
  }

  .sidebar-backdrop {
    position: absolute;
    inset: 0;
    z-index: 15;
    display: block;
    background: rgba(20, 22, 27, 0.24);
    backdrop-filter: blur(2px);
  }

  .chat-main {
    border-radius: var(--radius-lg);
  }

  .chat-header {
    padding: 14px 16px 13px;
  }

  .header-titles h1 {
    max-width: 46vw;
  }

  .message-scroll {
    padding: 20px 14px 6px;
  }

  .scroll-to-bottom {
    right: 20px;
    bottom: 118px;
  }

  .welcome-visual {
    transform: scale(0.9);
    margin-bottom: 22px;
  }
}

@media (max-width: 560px) {
  .chat-shell {
    padding: 0;
  }

  .chat-main {
    border: none;
    border-radius: 0;
  }

  .header-titles p {
    max-width: 42vw;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .batch-btn {
    padding: 0 9px;
  }

  .message-column {
    max-width: 100%;
  }

  .empty-chat {
    padding: 28px 4px 36px;
  }

  .suggestion-chip {
    height: auto;
    padding: 9px 13px;
    text-align: left;
    line-height: 1.5;
  }

  .welcome-state {
    padding: 28px 22px;
  }
}
</style>
