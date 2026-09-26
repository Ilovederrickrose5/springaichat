import { ref } from 'vue'

/**
 * SSE 流式聊天 composable。
 * 将流式请求、打字机、暂停/继续、AbortController 等逻辑从 Chat.vue 抽离。
 *
 * @param {Object} deps
 * @param {Ref}   deps.messages            消息列表 ref
 * @param {Ref}   deps.selectedConversation 当前会话 id ref
 * @param {Ref}   deps.isAtBottom          是否在底部附近 ref（滚动锁定）
 * @param {Function} deps.scrollToBottom   滚动到底部回调
 * @param {Function} deps.updateConversationInList 更新会话列表回调
 * @param {Function} deps.getAccessToken    读取 accessToken
 * @param {Function} deps.refreshToken      公共 refresh 函数
 * @param {Function} deps.clearAllAuth      清理本地认证态
 * @param {Object}   deps.axios             axios 实例
 * @param {Object}   deps.router            vue-router 实例
 * @param {Object}   deps.ElMessage         Element Plus 消息组件
 */
export function useStreamChat(deps) {
  const {
    messages, selectedConversation, isAtBottom,
    scrollToBottom, updateConversationInList,
    getAccessToken, refreshToken, clearAllAuth,
    axios, router, ElMessage
  } = deps

  const streamingContent = ref('')
  const isStreaming = ref(false)
  const isPaused = ref(false)
  const currentStreamController = ref(null)
  const typingSpeed = ref(10)

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

  // 主动中止当前流式请求（幂等）
  const abortCurrentStream = () => {
    if (currentStreamController.value) {
      currentStreamController.value.abort()
      currentStreamController.value = null
    }
  }

  const togglePause = () => {
    isPaused.value = !isPaused.value
  }

  const togglePauseMessage = (msg) => {
    if (msg.status === 'streaming') {
      isPaused.value = true
    } else if (msg.status === 'paused') {
      isPaused.value = false
    }
  }

  const sendMessageStream = async (content) => {
    if (!content || isStreaming.value) return

    isStreaming.value = true

    // 添加用户消息
    const userMsg = {
      id: Date.now(),
      conversationId: selectedConversation.value,
      role: 'user',
      content: content,
      createTime: new Date().toISOString()
    }
    messages.value.push(userMsg)

    // AI 占位消息
    const aiMsgPlaceholder = {
      id: Date.now() + 1,
      conversationId: selectedConversation.value,
      role: 'assistant',
      content: '',
      createTime: new Date().toISOString(),
      status: 'thinking',
      isPaused: false
    }
    messages.value.push(aiMsgPlaceholder)
    const aiMsgIndex = messages.value.length - 1
    streamingContent.value = ''

    scrollToBottom()

    const updateAiMessage = (text, status) => {
      messages.value[aiMsgIndex] = {
        ...messages.value[aiMsgIndex],
        content: text,
        status: status || messages.value[aiMsgIndex].status
      }
    }

    try {
      const token = getAccessToken()
      const controller = new AbortController()
      currentStreamController.value = controller

      const response = await fetch('http://localhost:8080/api/chat/messages/stream', {
        method: 'POST',
        headers: {
          'Accept': 'text/event-stream',
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        signal: controller.signal,
        body: JSON.stringify({
          conversationId: selectedConversation.value,
          content: content
        })
      })

      if (!response.ok) {
        const errorText = await response.text()
        if (response.status === 401) {
          try {
            await refreshToken()
            messages.value.splice(aiMsgIndex, 1)
            ElMessage.warning('登录状态已刷新，请重新发送消息')
            return
          } catch (se) {
            console.warn('SSE inline refresh failed', se)
          }
          clearAllAuth()
          messages.value.splice(aiMsgIndex, 1)
          router.push('/login')
          ElMessage.warning('登录已过期，请重新登录')
          return
        }
        throw new Error(`请求失败: ${response.status} ${errorText}`)
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      let pendingBuffer = ''

      if (!reader) throw new Error('浏览器未返回可读流')

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        pendingBuffer += decoder.decode(value, { stream: true })
        const events = pendingBuffer.split(/\r?\n\r?\n/)
        pendingBuffer = events.pop() || ''

        for (const event of events) {
          for (const line of event.split(/\r?\n/)) {
            if (line.startsWith('data:')) {
              const parsedContent = line.slice(5).replace(/^\s/, '')

              if (parsedContent.startsWith('{"error":')) {
                try {
                  const errorData = JSON.parse(parsedContent)
                  ElMessage.error(`请求失败: ${errorData.error}`)
                  messages.value.splice(aiMsgIndex, 1)
                  return
                } catch (e) { /* 非有效 JSON 错误，继续 */ }
              }

              for (const char of parsedContent) {
                if (controller.signal.aborted) break
                while (isPaused.value) {
                  messages.value[aiMsgIndex] = {
                    ...messages.value[aiMsgIndex],
                    status: 'paused'
                  }
                  await sleep(50)
                }
                if (streamingContent.value.length === 0) {
                  streamingContent.value += char
                  updateAiMessage(streamingContent.value, 'streaming')
                } else {
                  streamingContent.value += char
                  updateAiMessage(streamingContent.value)
                }
                if (isAtBottom.value) scrollToBottom()
                await sleep(typingSpeed.value)
              }
            }
          }
        }
      }

      // 缓冲区剩余数据
      if (pendingBuffer.trim()) {
        for (const line of pendingBuffer.split(/\r?\n/)) {
          if (line.startsWith('data:')) {
            const tail = line.slice(5).replace(/^\s/, '')
            for (const char of tail) {
              if (controller.signal.aborted) break
              streamingContent.value += char
              updateAiMessage(streamingContent.value)
              if (isAtBottom.value) scrollToBottom()
              await sleep(typingSpeed.value)
            }
          }
        }
      }

      updateConversationInList()
    } catch (error) {
      if (error.name === 'AbortError') {
        console.info('流式请求已被主动取消')
        messages.value.splice(aiMsgIndex, 1)
        return
      }
      console.error('流式发送失败:', error)
      ElMessage.error('发送失败，请检查网络连接')
      messages.value.splice(aiMsgIndex, 1)
    } finally {
      isStreaming.value = false
      isPaused.value = false
      currentStreamController.value = null
      if (messages.value[aiMsgIndex]) {
        messages.value[aiMsgIndex] = {
          ...messages.value[aiMsgIndex],
          status: 'done'
        }
      }
    }
  }

  return {
    streamingContent, isStreaming, isPaused, typingSpeed,
    currentStreamController,
    abortCurrentStream, togglePause, togglePauseMessage,
    sendMessageStream
  }
}
