<template>
  <div class="chat-container" :class="{ 'collapsed-sidebar': isCollapsed }">
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

    <div class="chat-window">
      <div v-if="selectedConversation" class="chat-content">
        <div class="chat-header">
          <el-link
            v-show="isCollapsed"
            type="primary"
            :underline="false"
            class="expand-btn"
            @click="toggleCollapse"
          >
            <el-icon><ArrowRight /></el-icon>
          </el-link>
          <h3>{{ currentConversation?.title || '对话' }}</h3>
          <div class="header-actions">
            <el-button
              v-if="selectedMessages.length > 0"
              type="danger"
              size="small"
              @click="batchDeleteMessages"
            >
              <el-icon><Delete /></el-icon>
              批量删除 ({{ selectedMessages.length }})
            </el-button>
            <el-link type="primary" :underline="false" @click="logout">
              <el-icon><WindPower /></el-icon>
              退出登录
            </el-link>
          </div>
        </div>

        <div
          ref="messageList"
          class="message-list"
          @click="closeContextMenu"
          @scroll="handleScroll"
        >
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
            <el-icon :size="64" color="#ccc"><ChatDotRound /></el-icon>
            <p>开始与AI对话吧</p>
          </div>
        </div>

        <div
          v-if="!isAtBottom"
          class="scroll-to-bottom-btn"
          @click="scrollToBottomFromButton"
        >
          <el-icon :size="18"><ArrowDown /></el-icon>
        </div>

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
          @send="sendMessageAction"
        />
      </div>

      <div v-else class="default-state">
        <el-icon :size="80" color="#ccc"><ChatLineRound /></el-icon>
        <h3>你好，我是企智通</h3>
        <p>你的企业知识助手，开始新对话吧</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios, { clearAllAuth, getAccessToken } from '@/utils/axios'
import { refreshToken } from '@/utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useStreamChat } from '@/composables/useStreamChat'
import ConversationList from '@/components/ConversationList.vue'
import MessageItem from '@/components/MessageItem.vue'
import ContextMenu from '@/components/ContextMenu.vue'
import InputArea from '@/components/InputArea.vue'

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
const isCollapsed = ref(false)
const currentUser = ref(localStorage.getItem('username') || '用户')
const selectedMessages = ref([])
const showContextMenu = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuMessageId = ref(null)

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
  abortCurrentStream,
  togglePauseMessage,
  sendMessageStream
} = useStreamChat({
  messages, selectedConversation, isAtBottom,
  scrollToBottom, updateConversationInList,
  getAccessToken, refreshToken, clearAllAuth,
  axios, router, ElMessage
})

// ===== 右键菜单 =====
const handleContextMenu = (event, msg) => {
  event.preventDefault()
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuMessageId.value = msg.id
  showContextMenu.value = true
}
const closeContextMenu = () => { showContextMenu.value = false }
const toggleSelectFromMenu = () => {
  if (contextMenuMessageId.value) {
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
    if (response.success) conversations.value = response.data || []
  } catch (e) {
    ElMessage.error('加载会话列表失败')
  }
}
const loadMessages = async (conversationId) => {
  loading.value = true
  try {
    const response = await axios.get(`/chat/conversations/${conversationId}/messages`)
    if (response.success) {
      messages.value = response.data || []
      nextTick(scrollToBottom)
    }
  } catch (e) {
    ElMessage.error('加载消息失败')
  } finally {
    loading.value = false
  }
}
const createConversation = async () => {
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
  abortCurrentStream()
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

// 同步发送（后备）
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return
  const content = inputMessage.value.trim()
  inputMessage.value = ''
  loading.value = true
  messages.value.push({
    id: Date.now(), conversationId: selectedConversation.value,
    role: 'user', content, createTime: new Date().toISOString()
  })
  nextTick(scrollToBottom)
  try {
    const response = await axios.post('/chat/messages', {
      conversationId: selectedConversation.value, content
    })
    if (response.success) {
      messages.value.push(response.data)
      updateConversationInList()
    } else {
      ElMessage.error(response.message || '发送失败')
    }
  } catch (error) {
    ElMessage.error('发送失败，请检查网络连接')
  } finally {
    loading.value = false
    nextTick(scrollToBottom)
  }
}

const sendMessageAction = async () => {
  await sendMessageStream(inputMessage.value.trim())
  inputMessage.value = ''
}

// ===== 工具 =====
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
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

onMounted(() => {
  loadConversations()
})
</script>

<style scoped>
.el-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  font-size: 14px;
  padding: 4px 8px;
}
.el-link .el-icon { margin-right: 4px; }
.el-link:last-child .el-icon { margin-right: 0; }

.chat-container {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
  overflow: hidden;
  position: relative;
}
.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
}
.chat-header {
  padding: 20px;
  background: white;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chat-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 12px;
}
.expand-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  margin-right: 8px;
  transition: background-color 0.2s;
  flex-shrink: 0;
}
.expand-btn:hover {
  background-color: #f5f5f5;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}
.scroll-to-bottom-btn {
  position: absolute;
  right: 24px;
  bottom: 90px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #667eea;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(102, 126, 234, 0.4);
  z-index: 10;
  transition: opacity 0.2s;
}
.scroll-to-bottom-btn:hover {
  background: #5568d3;
}
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #999;
}
.empty-chat p {
  margin-top: 16px;
}
.default-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}
.default-state h3 {
  margin: 16px 0 8px;
  font-size: 18px;
  font-weight: 500;
  color: #666;
}
</style>
