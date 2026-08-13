<template>
  <div class="chat-container" :class="{ 'collapsed-sidebar': isCollapsed }">
    <!-- 左侧会话列表 - 固定定位，不随页面滚动 -->
    <div class="conversation-list" :class="{ collapsed: isCollapsed }">
      <!-- 侧边栏头部（包含收起按钮） -->
      <div class="list-header">
        <div class="header-left">
          <el-link
            type="primary"
            :underline="false"
            class="collapse-btn"
            @click="toggleCollapse"
          >
            <el-icon><ArrowLeft /></el-icon>
          </el-link>
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
            <el-icon :size="20" color="#667eea" class="conv-icon"><ChatLineRound /></el-icon>
            <div class="conv-content">
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
            </div>
          </div>
          <el-link
            type="danger"
            :underline="false"
            class="delete-btn"
            @click.stop="deleteConversation(conv.id)"
          >
            <el-icon><Delete /></el-icon>
          </el-link>
        </div>

        <div v-if="conversations.length === 0" class="empty-state">
          <el-icon :size="48" color="#ccc">
            <ChatLineRound />
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
        <el-link
          type="primary"
          :underline="false"
          class="logout-btn"
          @click="logout"
        >
          <el-icon><WindPower /></el-icon>
        </el-link>
      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div class="chat-window">
      <div v-if="selectedConversation" class="chat-content">
        <!-- 聊天头部 -->
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
            <el-link
              type="primary"
              :underline="false"
              @click="logout"
            >
              <el-icon><WindPower /></el-icon>
              退出登录
            </el-link>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messageList" class="message-list" @click="closeContextMenu">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message-item"
            :class="[
              msg.role === 'user' ? 'user-message' : 'ai-message',
              { 'selected': selectedMessages.includes(msg.id) }
            ]"
            @contextmenu="(e) => handleContextMenu(e, msg)"
          >
            <div class="message-checkbox">
              <el-checkbox
                :checked="selectedMessages.includes(msg.id)"
                @change="(val) => toggleMessageSelect(msg.id, val)"
              />
            </div>
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="32" color="#667eea">
                <User />
              </el-icon>
              <el-icon v-else :size="32" color="#764ba2">
                <ChatDotRound />
              </el-icon>
            </div>
            <div class="message-content">
              <div class="message-text">
                <span v-if="msg.status === 'thinking' && !msg.content" class="thinking-indicator">
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="thinking-text">思考中...</span>
                </span>
                <template v-else>{{ msg.content }}</template>
              </div>
              <div class="message-footer">
                <span class="message-time">{{ formatTime(msg.createTime) }}</span>
                <div v-if="msg.role === 'assistant' && (msg.status === 'streaming' || msg.status === 'paused')" class="message-controls">
                  <el-link
                    type="primary"
                    :underline="false"
                    class="pause-btn"
                    @click.stop="togglePauseMessage(msg)"
                  >
                    <el-icon><Pause v-if="msg.status === 'streaming'" /><Play v-else /></el-icon>
                    {{ msg.status === 'streaming' ? '暂停' : '继续' }}
                  </el-link>
                </div>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-if="!loading && messages.length === 0" class="empty-chat">
            <el-icon :size="64" color="#ccc">
              <ChatDotRound />
            </el-icon>
            <p>开始与AI对话吧</p>
          </div>
        </div>

        <!-- 右键菜单 -->
        <div
          v-if="showContextMenu"
          class="context-menu"
          :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
          @click.stop
        >
          <div class="context-menu-item" @click="toggleSelectFromMenu">
            <el-icon><Check /></el-icon>
            {{ selectedMessages.includes(contextMenuMessageId) ? '取消选择' : '选择' }}
          </div>
          <div class="context-menu-divider"></div>
          <div class="context-menu-item danger" @click="deleteFromMenu">
            <el-icon><Delete /></el-icon>
            删除消息
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
            <el-icon><Promotion /></el-icon>
            {{ isStreaming ? '生成中...' : '发送' }}
          </el-button>
        </div>
      </div>

      <!-- 未选择会话时的默认状态 -->
      <div v-else class="default-state">
        <el-icon :size="80" color="#ccc">
          <ChatLineRound />
        </el-icon>
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
import { ElMessage, ElMessageBox } from 'element-plus'
// Element Plus 图标已在 main.js 中全局注册，模板中可直接使用，无需 import

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
// 打字机速度（毫秒/字符，值越大越慢）
const typingSpeed = ref(10)

// 延迟函数
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))
// 侧边栏收起状态
const isCollapsed = ref(false)
// 当前登录用户
const currentUser = ref(localStorage.getItem('username') || '用户')
// 聊天容器引用
const chatContainer = ref(null)
// 选中的消息ID列表（用于批量删除）
const selectedMessages = ref([])
// 右键菜单状态
const showContextMenu = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuMessageId = ref(null)
// 暂停输出状态
const isPaused = ref(false)

// 切换侧边栏收起/展开
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

// 右键菜单处理
const handleContextMenu = (event, msg) => {
  event.preventDefault()
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuMessageId.value = msg.id
  showContextMenu.value = true
}

const closeContextMenu = () => {
  showContextMenu.value = false
}

const toggleSelectFromMenu = () => {
  if (contextMenuMessageId.value) {
    const idx = selectedMessages.value.indexOf(contextMenuMessageId.value)
    if (idx > -1) {
      selectedMessages.value.splice(idx, 1)
    } else {
      selectedMessages.value.push(contextMenuMessageId.value)
    }
  }
  closeContextMenu()
}

const deleteFromMenu = () => {
  if (contextMenuMessageId.value) {
    deleteMessage(contextMenuMessageId.value)
  }
  closeContextMenu()
}

// 暂停/继续输出
const togglePause = () => {
  isPaused.value = !isPaused.value
}

// 从消息上触发暂停/继续
const togglePauseMessage = (msg) => {
  if (msg.status === 'streaming') {
    isPaused.value = true
  } else if (msg.status === 'paused') {
    isPaused.value = false
  }
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

// 切换消息选择状态
const toggleMessageSelect = (messageId, checked) => {
  if (checked) {
    if (!selectedMessages.value.includes(messageId)) {
      selectedMessages.value.push(messageId)
    }
  } else {
    selectedMessages.value = selectedMessages.value.filter(id => id !== messageId)
  }
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
      const response = await axios.post('/chat/messages/batch-delete', {
        messageIds: selectedMessages.value
      })
      if (response.success) {
        const deleteCount = selectedMessages.value.length
        messages.value = messages.value.filter(m => !selectedMessages.value.includes(m.id))
        selectedMessages.value = []
        ElMessage.success(`成功删除 ${deleteCount} 条消息`)
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

  // 创建 AI 消息占位符并加入消息列表
  const aiMsgPlaceholder = {
    id: Date.now() + 1,
    conversationId: selectedConversation.value,
    role: 'assistant',
    content: '',
    createTime: new Date().toISOString(),
    status: 'thinking', // thinking, streaming, paused, done
    isPaused: false
  }
  messages.value.push(aiMsgPlaceholder)
  const aiMsgIndex = messages.value.length - 1
  streamingContent.value = ''

  nextTick(() => {
    scrollToBottom()
  })

  // 辅助函数：通过数组索引赋值强制触发 Vue 响应式更新
  const updateAiMessage = (content, status) => {
    messages.value[aiMsgIndex] = {
      ...messages.value[aiMsgIndex],
      content: content,
      status: status || messages.value[aiMsgIndex].status
    }
  }

  try {
    // 获取 JWT token（使用统一的 accessToken 读取，兼容老 token 字段）
    const token = getAccessToken()
    const requestHeaders = {
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    }

    // 使用 fetch 进行 SSE 请求
    const response = await fetch('http://localhost:8080/api/chat/messages/stream', {
      method: 'POST',
      headers: requestHeaders,
      body: JSON.stringify({
        conversationId: selectedConversation.value,
        content: content
      })
    })

    if (!response.ok) {
      const errorText = await response.text()
      // SSE 请求 401：accessToken 过期了，尝试 refresh 一次；成功就提示用户重发，失败再清 token 跳登录
      if (response.status === 401) {
        try {
          const refreshToken = localStorage.getItem('refreshToken')
          if (refreshToken) {
            const refreshRes = await fetch('http://localhost:8080/api/auth/refresh', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ refreshToken })
            })
            if (refreshRes.ok) {
              const refreshBody = await refreshRes.json()
              if (refreshBody && refreshBody.success) {
                // 保存新双 token 后，让用户重新发消息（SSE 流式重发需要重新构造请求太复杂，这里选择友好提示）
                if (refreshBody.accessToken) {
                  localStorage.setItem('accessToken', refreshBody.accessToken)
                  localStorage.setItem('token', refreshBody.accessToken)
                }
                if (refreshBody.refreshToken) {
                  localStorage.setItem('refreshToken', refreshBody.refreshToken)
                }
                messages.value.splice(aiMsgIndex, 1)
                ElMessage.warning('登录状态已刷新，请重新发送消息')
                return
              }
            }
          }
        } catch (se) {
          console.warn('SSE inline refresh failed', se)
        }
        // refresh 没成功 → 清 token 跳登录
        clearAllAuth()
        messages.value.splice(aiMsgIndex, 1)
        router.push('/login')
        ElMessage.warning('登录已过期，请重新登录')
        return
      }
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

      // 按 SSE 协议解析：事件之间以双换行分隔
      const events = pendingBuffer.split(/\r?\n\r?\n/)
      pendingBuffer = events.pop() || ''

      for (const event of events) {
        const lines = event.split(/\r?\n/)
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const parsedContent = line.slice(5).replace(/^\s/, '')
            
            // 检查是否为错误消息
            if (parsedContent.startsWith('{"error":')) {
              try {
                const errorData = JSON.parse(parsedContent)
                ElMessage.error(`请求失败: ${errorData.error}`)
                messages.value.splice(aiMsgIndex, 1)
                return
              } catch (e) {
                // 不是有效的 JSON 错误，继续正常处理
              }
            }
            
            // 逐个字符添加延迟，实现打字机效果
            for (const char of parsedContent) {
              // 暂停检查
              while (isPaused.value) {
                messages.value[aiMsgIndex] = {
                  ...messages.value[aiMsgIndex],
                  status: 'paused'
                }
                await sleep(50)
              }
              // 首次收到内容，从 thinking 改为 streaming
              if (streamingContent.value.length === 0) {
                streamingContent.value += char
                updateAiMessage(streamingContent.value, 'streaming')
              } else {
                streamingContent.value += char
                updateAiMessage(streamingContent.value)
              }
              nextTick(() => {
                scrollToBottom()
              })
              // 添加打字延迟
              await sleep(typingSpeed.value)
            }
          }
        }
      }
    }

    // 处理缓冲区中剩余的数据
    if (pendingBuffer.trim()) {
      const lines = pendingBuffer.split(/\r?\n/)
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const parsedTailContent = line.slice(5).replace(/^\s/, '')
          // 逐个字符添加延迟
          for (const char of parsedTailContent) {
            streamingContent.value += char
            updateAiMessage(streamingContent.value)
            await sleep(typingSpeed.value)
          }
        }
      }
    }

    // 更新会话列表中的标题和时间
    updateConversationInList()

  } catch (error) {
    console.error('流式发送失败:', error)
    ElMessage.error('发送失败，请检查网络连接')
    // 移除失败的 AI 消息
    messages.value.splice(aiMsgIndex, 1)
  } finally {
    isStreaming.value = false
    isPaused.value = false
    // 更新消息状态为 done
    if (messages.value[aiMsgIndex]) {
      messages.value[aiMsgIndex] = {
        ...messages.value[aiMsgIndex],
        status: 'done'
      }
    }
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

// 退出登录：先通知后端拉黑 access + 删除 refresh，再清本地
const logout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(async () => {
    try {
      await axios.post('/auth/logout')
    } catch (e) {
      // 后端即使报错，也按「本地清理 + 退出」处理
      console.warn('logout api call failed, continue to clear local state', e)
    }
    clearAllAuth()
    currentUser.value = '用户'
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {
    // 用户取消
  })
}
</script>

<style scoped>
/* el-link 样式覆盖：让其表现与 el-button 一致 */
.el-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  font-size: 14px;
  padding: 4px 8px;
}
.el-link .el-icon {
  margin-right: 4px;
}
.el-link:last-child .el-icon {
  margin-right: 0;
}

.chat-container {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
  overflow: hidden;
  position: relative;
}

.conversation-list {
  width: 320px;
  background: white;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, padding 0.3s ease, border 0.3s ease;
  flex-shrink: 0;
  overflow: hidden;
}

.conversation-list.collapsed {
  width: 0;
  padding: 0;
  border-right: none;
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

/* 折叠状态下显示的展开按钮 - 放在 chat-header 内联位置 */
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

/* 思考中指示器 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #764ba2;
  animation: thinking-bounce 1.4s infinite ease-in-out both;
}

.thinking-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.thinking-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes thinking-bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.thinking-text {
  font-size: 14px;
  color: #999;
  margin-left: 4px;
}

/* 消息控制按钮 */
.message-controls {
  display: flex;
  gap: 8px;
}

.pause-btn {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.pause-btn:hover {
  background-color: rgba(118, 75, 162, 0.1);
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
  display: none;
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

/* 右键菜单样式 */
.context-menu {
  position: fixed;
  min-width: 160px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  padding: 4px 0;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: #303133;
  cursor: pointer;
  transition: background-color 0.2s;
}

.context-menu-item:hover {
  background-color: #f5f7fa;
}

.context-menu-item.danger {
  color: #f56c6c;
}

.context-menu-item.danger:hover {
  background-color: #fef0f0;
}

.context-menu-divider {
  height: 1px;
  background-color: #e8e8e8;
  margin: 4px 0;
}
</style>
