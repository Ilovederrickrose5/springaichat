<template>
  <div class="chat-container" :class="{ 'collapsed-sidebar': isCollapsed }">
    <!-- 左侧会话列表 - 固定定位，不随页面滚动 -->
    <div class="conversation-list" :class="{ collapsed: isCollapsed }">
      <!-- 侧边栏头部（包含收起按钮） -->
      <div class="list-header">
        <div class="header-left">
          <el-button
            type="text"
            size="small"
            class="collapse-btn"
            @click="toggleCollapse"
          >
            <el-icon><ChevronLeft /></el-icon>
          </el-button>
          <h2 v-show="!isCollapsed">对话列表</h2>
        </div>
        <el-button
          type="primary"
          size="small"
          @click="createConversation"
        >
          <el-icon><Plus /></el-icon>
          <span v-show="!isCollapsed">新建会话</span>
        </el-button>
      </div>

      <div class="conversations">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: selectedConversation === conv.id }"
          @click="selectConversation(conv.id)"
        >
          <div class="conv-info">
            <el-icon :size="20" color="#667eea" class="conv-icon"><MessageSquare /></el-icon>
            <div class="conv-content">
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
            </div>
          </div>
          <el-button
            type="text"
            size="small"
            class="delete-btn"
            @click.stop="deleteConversation(conv.id)"
          >
            <el-icon><Trash /></el-icon>
          </el-button>
        </div>

        <div v-if="conversations.length === 0" class="empty-state">
          <el-icon :size="48" color="#ccc">
            <MessageSquare />
          </el-icon>
          <p v-show="!isCollapsed">暂无对话</p>
          <el-button v-show="!isCollapsed" type="primary" size="small" @click="createConversation">
            开始新对话
          </el-button>
        </div>
      </div>

      <!-- 用户信息区域 -->
      <div class="user-info" v-show="!isCollapsed">
        <div class="user-avatar">
          <el-icon :size="32" color="#667eea"><User /></el-icon>
        </div>
        <div class="user-details">
          <div class="user-name">@{{ currentUser }}</div>
          <div class="user-status">
            <span class="status-dot online"></span>
            <span>在线</span>
          </div>
        </div>
        <el-button
          type="text"
          size="small"
          class="logout-btn"
          @click="logout"
        >
          <el-icon><Power /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div class="chat-window">
      <div v-if="selectedConversation" class="chat-content">
        <!-- 聊天头部 -->
        <div class="chat-header">
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
            <el-button
              type="text"
              size="small"
              @click="logout"
            >
              <el-icon><Power /></el-icon>
              退出登录
            </el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messageList" class="message-list">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-item"
            :class="[
              msg.role === 'user' ? 'user-message' : 'ai-message',
              { 'selected': selectedMessages.includes(msg.id) }
            ]"
          >
            <div class="message-checkbox">
              <el-checkbox
                :value="msg.id"
                v-model="selectedMessages"
                @change="onMessageSelect"
              />
            </div>
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="32" color="#667eea">
                <User />
              </el-icon>
              <el-icon v-else :size="32" color="#764ba2">
                <Robot />
              </el-icon>
            </div>
            <div class="message-content">
              <div class="message-text">{{ msg.content }}</div>
              <div class="message-footer">
                <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                <el-button
                  type="text"
                  size="mini"
                  class="delete-message-btn"
                  @click.stop="deleteMessage(msg.id)"
                >
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <!-- 加载动画 -->
          <div v-if="loading" class="loading-item">
            <div class="loading-content">
              <el-icon class="loading-icon" :size="24" color="#667eea">
                <Loading />
              </el-icon>
              <span>AI正在思考中...</span>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-if="!loading && messages.length === 0" class="empty-chat">
            <el-icon :size="64" color="#ccc">
              <MessageCircle />
            </el-icon>
            <p>开始与AI对话吧</p>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="inputMessage"
            placeholder="输入消息..."
            :disabled="isStreaming"
            @keyup.enter="sendMessageAction"
          />
          <el-button
            type="primary"
            :loading="isStreaming"
            :disabled="!inputMessage.trim() || isStreaming"
            @click="sendMessageAction"
          >
            <el-icon><Send /></el-icon>
            {{ isStreaming ? '生成中...' : '发送' }}
          </el-button>
        </div>
      </div>

      <!-- 未选择会话时的默认状态 -->
      <div v-else class="default-state">
        <el-icon :size="80" color="#ccc">
          <MessageSquare />
        </el-icon>
        <h3>选择一个会话开始聊天</h3>
        <p>或创建新的会话</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from '@/utils/axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

const router = useRouter()

const conversations = ref([])
const selectedConversation = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messageList = ref(null)
// 流式输出相关
const streamingContent = ref('')
const isStreaming = ref(false)
// 侧边栏收起状态
const isCollapsed = ref(false)
// 当前登录用户
const currentUser = ref(localStorage.getItem('username') || '用户')
// 聊天容器引用
const chatContainer = ref(null)
// 选中的消息ID列表（用于批量删除）
const selectedMessages = ref([])

// 切换侧边栏收起/展开
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const currentConversation = computed(() => {
  return conversations.value.find(c => c.id === selectedConversation.value)
})

// 初始化加载会话列表
onMounted(() => {
  loadConversations()
})

// 监听选中会话变化，加载消息
watch(selectedConversation, (newVal) => {
  if (newVal) {
    loadMessages(newVal)
  }
})

// 加载会话列表
const loadConversations = async () => {
  try {
    const response = await axios.get('/chat/conversations')
    if (response.success) {
      conversations.value = response.data
      // 如果有会话，默认选中第一个
      if (conversations.value.length > 0 && !selectedConversation.value) {
        selectedConversation.value = conversations.value[0].id
      }
    }
  } catch (error) {
    ElMessage.error('加载会话列表失败')
  }
}

// 加载会话消息
const loadMessages = async (conversationId) => {
  try {
    const response = await axios.get(`/chat/conversations/${conversationId}/messages`)
    if (response.success) {
      messages.value = response.data
      nextTick(() => {
        scrollToBottom()
      })
    }
  } catch (error) {
    ElMessage.error('加载消息失败')
  }
}

// 创建新会话
const createConversation = async () => {
  try {
    const response = await axios.post('/chat/conversations')
    if (response.success) {
      const newConv = response.data
      conversations.value.unshift(newConv)
      selectedConversation.value = newConv.id
      messages.value = []
      ElMessage.success('会话创建成功')
    }
  } catch (error) {
    ElMessage.error('创建会话失败')
  }
}

// 选择会话
const selectConversation = (conversationId) => {
  selectedConversation.value = conversationId
}

// 删除会话
const deleteConversation = async (conversationId) => {
  ElMessageBox.confirm(
    '确定要删除这个会话吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response = await axios.delete(`/chat/conversations/${conversationId}`)
      if (response.success) {
        conversations.value = conversations.value.filter(c => c.id !== conversationId)
        if (selectedConversation.value === conversationId) {
          selectedConversation.value = conversations.value.length > 0 ? conversations.value[0].id : null
          messages.value = []
        }
        ElMessage.success('删除成功')
      }
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {
    // 用户取消
  })
}

// 删除消息
const deleteMessage = async (messageId) => {
  ElMessageBox.confirm(
    '确定要删除这条消息吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
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
  }).catch(() => {
    // 用户取消
  })
}

// 消息选择变化
const onMessageSelect = () => {
  // 可选：点击消息内容时取消选择
}

// 批量删除消息
const batchDeleteMessages = async () => {
  if (selectedMessages.value.length === 0) return

  ElMessageBox.confirm(
    `确定要删除选中的 ${selectedMessages.value.length} 条消息吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response = await axios.delete('/chat/messages/batch', {
        data: { messageIds: selectedMessages.value }
      })
      if (response.success) {
        messages.value = messages.value.filter(m => !selectedMessages.value.includes(m.id))
        selectedMessages.value = []
        ElMessage.success(`成功删除 ${selectedMessages.value.length} 条消息`)
      }
    } catch (error) {
      ElMessage.error('批量删除失败')
    }
  }).catch(() => {
    // 用户取消
  })
}

// 发送消息（同步方式 - 保留作为后备）
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  const content = inputMessage.value.trim()
  inputMessage.value = ''
  loading.value = true

  // 添加用户消息到列表
  const userMsg = {
    id: Date.now(),
    conversationId: selectedConversation.value,
    role: 'user',
    content: content,
    createTime: new Date().toISOString()
  }
  messages.value.push(userMsg)

  nextTick(() => {
    scrollToBottom()
  })

  try {
    const response = await axios.post('/chat/messages', {
      conversationId: selectedConversation.value,
      content: content
    })

    if (response.success) {
      const aiMsg = response.data
      messages.value.push(aiMsg)

      // 更新会话列表中的标题和时间
      updateConversationInList()

      ElMessage.success('消息发送成功')
    } else {
      ElMessage.error(response.message || '发送失败')
    }
  } catch (error) {
    ElMessage.error('发送失败，请检查网络连接')
  } finally {
    loading.value = false
    nextTick(() => {
      scrollToBottom()
    })
  }
}

// 发送消息（流式方式 - 主要使用）
const sendMessageStream = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return

  const content = inputMessage.value.trim()
  inputMessage.value = ''
  isStreaming.value = true

  // 添加用户消息到列表
  const userMsg = {
    id: Date.now(),
    conversationId: selectedConversation.value,
    role: 'user',
    content: content,
    createTime: new Date().toISOString()
  }
  messages.value.push(userMsg)

  // 创建 AI 消息占位符
  const aiMsg = {
    id: Date.now() + 1,
    conversationId: selectedConversation.value,
    role: 'assistant',
    content: '',
    createTime: new Date().toISOString()
  }
  messages.value.push(aiMsg)
  streamingContent.value = ''

  nextTick(() => {
    scrollToBottom()
  })

  try {
    // 获取 JWT token
    const token = localStorage.getItem('token')
    const requestHeaders = {
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    }

    console.log('[流式发送] token =', token)
    console.log('[流式发送] request url =', 'http://localhost:8080/api/chat/messages/stream')
    console.log('[流式发送] request method =', 'POST')
    console.log('[流式发送] request headers =', requestHeaders)

    // 使用 EventSource 或 fetch 进行 SSE 请求
    const response = await fetch('http://localhost:8080/api/chat/messages/stream', {
      method: 'POST',
      headers: requestHeaders,
      body: JSON.stringify({
        conversationId: selectedConversation.value,
        content: content
      })
    })

    console.log('[流式发送] response status =', response.status)
    console.log('[流式发送] response ok =', response.ok)

    if (!response.ok) {
      const errorText = await response.text()
      console.log('[流式发送] response body =', errorText)
      throw new Error(`请求失败: ${response.status} ${errorText}`)
    }

    // 读取流数据
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let pendingBuffer = ''

    if (!reader) {
      throw new Error('浏览器未返回可读流')
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      pendingBuffer += chunk
      console.log('[流式发送] raw chunk =', chunk)

      const events = pendingBuffer.split(/\r?\n\r?\n/)
      pendingBuffer = events.pop() || ''

      for (const event of events) {
        const lines = event.split(/\r?\n/)
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const parsedContent = line.slice(5).replace(/^\s/, '')
            console.log('[流式发送] parsed content =', parsedContent)
            streamingContent.value += parsedContent
            aiMsg.content = streamingContent.value
            nextTick(() => {
              scrollToBottom()
            })
          }
        }
      }
    }

    if (pendingBuffer.trim()) {
      const lines = pendingBuffer.split(/\r?\n/)
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const parsedTailContent = line.slice(5).replace(/^\s/, '')
          console.log('[流式发送] parsed tail content =', parsedTailContent)
          streamingContent.value += parsedTailContent
          aiMsg.content = streamingContent.value
        }
      }
    }

    // 更新会话列表中的标题和时间
    updateConversationInList()

  } catch (error) {
    console.error('流式发送失败:', error)
    ElMessage.error('发送失败，请检查网络连接')
    // 移除失败的 AI 消息
    messages.value = messages.value.filter(m => m.id !== aiMsg.id)
  } finally {
    isStreaming.value = false
  }
}

// 发送消息 - 选择使用流式还是同步
const sendMessageAction = async () => {
  // 默认使用流式方式
  await sendMessageStream()
}

// 更新会话列表
const updateConversationInList = () => {
  const convIndex = conversations.value.findIndex(c => c.id === selectedConversation.value)
  if (convIndex !== -1) {
    conversations.value[convIndex].title = currentConversation.value?.title || '新对话'
    conversations.value[convIndex].updateTime = new Date().toISOString()
    // 移到列表顶部
    const [removed] = conversations.value.splice(convIndex, 1)
    conversations.value.unshift(removed)
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // 小于1分钟
  if (diff < 60000) {
    return '刚刚'
  }
  // 小于1小时
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  }
  // 今天
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  // 今年
  if (date.getFullYear() === now.getFullYear()) {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  // 其他
  return date.toLocaleDateString('zh-CN')
}

// 退出登录
const logout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    router.push('/')
    ElMessage.success('已退出登录')
  }).catch(() => {
    // 用户取消
  })
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
  overflow: hidden;
}

.conversation-list {
  width: 320px;
  background: white;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;
}

.conversation-list.collapsed {
  width: 60px;
}

.list-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.collapse-btn:hover {
  background-color: #f5f5f5;
}

.conversation-list.collapsed .collapse-btn {
  transform: rotate(180deg);
}

.list-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.conversations {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color 0.2s;
}

.conversation-item:hover {
  background-color: #fafafa;
}

.conversation-item.active {
  background-color: #f0f5ff;
}

.conv-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.conv-icon {
  flex-shrink: 0;
}

.conv-content {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.delete-btn {
  opacity: 0;
  color: #999;
  transition: opacity 0.2s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #f56c6c;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
}

.empty-state p {
  margin: 16px 0;
}

/* 用户信息区域 */
.user-info {
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f5ff 100%);
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #67c23a;
  margin-top: 2px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #999;
}

.status-dot.online {
  background-color: #67c23a;
}

.logout-btn {
  color: #999;
  transition: color 0.2s;
}

.logout-btn:hover {
  color: #f56c6c;
}

/* 右侧聊天窗口 */
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

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  max-width: 80%;
  align-items: flex-start;
  transition: background-color 0.2s;
}

.message-item:hover {
  background-color: rgba(0, 0, 0, 0.02);
}

.message-item.selected {
  background-color: rgba(102, 126, 234, 0.1);
  border-radius: 12px;
  padding: 8px;
}

.user-message {
  margin-left: auto;
  flex-direction: row-reverse;
}

.user-message .message-content {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 16px 16px 4px 16px;
}

.ai-message .message-content {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 16px 16px 16px 4px;
}

.message-checkbox {
  margin: 0 8px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-item:hover .message-checkbox {
  opacity: 1;
}

.message-item.selected .message-checkbox {
  opacity: 1;
}

.user-message .message-checkbox {
  order: 2;
}

.message-avatar {
  margin: 0 8px;
  flex-shrink: 0;
}

.message-content {
  padding: 12px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.message-time {
  font-size: 12px;
  color: #999;
}

.user-message .message-time {
  color: rgba(255, 255, 255, 0.7);
}

.delete-message-btn {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  opacity: 0;
  transition: opacity 0.2s;
}

.message-item:hover .delete-message-btn {
  opacity: 1;
}

.delete-message-btn:hover {
  color: #fff;
}

.ai-message .delete-message-btn {
  color: #999;
}

.ai-message .delete-message-btn:hover {
  color: #f56c6c;
}

.loading-item {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 20px;
}

.loading-content {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  color: #667eea;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
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

.input-area {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e8e8e8;
  display: flex;
  gap: 12px;
}

.input-area .el-input {
  flex: 1;
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
