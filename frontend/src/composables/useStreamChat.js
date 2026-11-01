import { ref } from 'vue'
import BASE_URL from '../api/config'
import { parseSseEvents } from '../utils/sseParser.js'

/**
 * SSE 流式聊天 composable。
 * 将流式请求、打字机、暂停/继续、AbortController 等逻辑从 Chat.vue 抽离。
 *
 * 消息更新一律按本地 id 查找，避免流式过程中列表被删除/切换导致下标错位。
 *
 * @param {Object} deps
 * @param {Ref}   deps.messages            消息列表 ref
 * @param {Ref}   deps.selectedConversation 当前会话 id ref
 * @param {Ref}   deps.isAtBottom          是否在底部附近 ref（滚动锁定）
 * @param {Function} deps.scrollToBottom   滚动到底部回调
 * @param {Function} deps.updateConversationInList 更新会话列表回调
 * @param {Function} deps.getAccessToken    读取 accessToken
 * @param {Function} deps.refreshToken      公共 refresh 函数（内部单飞）
 * @param {Function} deps.clearAllAuth      清理本地认证态
 * @param {Object}   deps.router            vue-router 实例
 * @param {Object}   deps.ElMessage         Element Plus 消息组件
 */
export function useStreamChat(deps) {
  const {
    messages, selectedConversation, isAtBottom,
    scrollToBottom, updateConversationInList,
    getAccessToken, refreshToken, clearAllAuth,
    router, ElMessage
  } = deps

  const streamingContent = ref('')
  const isStreaming = ref(false)
  const isPaused = ref(false)
  const currentStreamController = ref(null)
  const typingSpeed = ref(10)
  const activeAiId = ref(null)

  let streamSequence = 0
  let localIdSeq = 0
  let scrollFrame = null

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))
  const nextLocalId = () => `local-${Date.now()}-${++localIdSeq}`

  // 流式期间每帧最多滚动一次，避免逐字符改 scrollTop
  const requestScroll = () => {
    if (scrollFrame) return
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = null
      scrollToBottom()
    })
  }

  const findIndexById = (id) => messages.value.findIndex(m => m.id === id)

  // 结束当前 AI 占位消息：无内容则移除，有内容则标记完成
  const settleActiveMessage = () => {
    const id = activeAiId.value
    activeAiId.value = null
    if (id === null) return

    const idx = findIndexById(id)
    if (idx === -1) return
    if (!messages.value[idx].content) {
      messages.value.splice(idx, 1)
    } else {
      messages.value[idx] = { ...messages.value[idx], status: 'done' }
    }
  }

  // 主动中止当前流式请求（幂等）：保留已生成的部分内容
  const abortCurrentStream = () => {
    streamSequence++
    if (currentStreamController.value) {
      currentStreamController.value.abort()
      currentStreamController.value = null
    }
    settleActiveMessage()
    isStreaming.value = false
    isPaused.value = false
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

  async function openStream(conversationId, content, controller, allowRetry) {
    const response = await fetch(`${BASE_URL}/chat/messages/stream`, {
      method: 'POST',
      headers: {
        'Accept': 'text/event-stream',
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getAccessToken()}`
      },
      signal: controller.signal,
      body: JSON.stringify({ conversationId, content })
    })

    if (response.status === 401 && allowRetry) {
      try {
        await refreshToken()
      } catch (e) {
        clearAllAuth()
        router.push('/login')
        ElMessage.warning('登录已过期，请重新登录')
        return null
      }
      return openStream(conversationId, content, controller, false)
    }

    return response
  }

  const sendMessageStream = async (content) => {
    const text = (content || '').trim()
    if (!text || isStreaming.value) return

    const sequence = ++streamSequence
    const conversationId = selectedConversation.value
    const controller = new AbortController()
    currentStreamController.value = controller
    isStreaming.value = true
    isPaused.value = false
    streamingContent.value = ''

    const aiId = nextLocalId()
    activeAiId.value = aiId
    messages.value.push(
      {
        id: nextLocalId(),
        conversationId,
        role: 'user',
        content: text,
        createTime: new Date().toISOString()
      },
      {
        id: aiId,
        conversationId,
        role: 'assistant',
        content: '',
        createTime: new Date().toISOString(),
        status: 'thinking',
        isPaused: false
      }
    )

    const isCurrent = () => sequence === streamSequence && !controller.signal.aborted

    const updateAi = (patch) => {
      const idx = findIndexById(aiId)
      if (idx === -1) return
      messages.value[idx] = { ...messages.value[idx], ...patch }
    }

    requestScroll()

    try {
      const response = await openStream(conversationId, text, controller, true)
      if (!response || !isCurrent()) return

      if (!response.ok) {
        const errorText = await response.text()
        let detail = ''
        try {
          const body = JSON.parse(errorText)
          detail = body.message || (body.errors && Object.values(body.errors)[0]) || ''
        } catch (e) {
          detail = errorText
        }
        const failure = new Error(detail || `请求失败: ${response.status}`)
        failure.userMessage = detail
        throw failure
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('浏览器未返回可读流')

      const decoder = new TextDecoder()
      let pendingBuffer = ''
      let stopped = false

      const consume = async (events) => {
        for (const { event, data } of events) {
          if (event === 'error') {
            ElMessage.error(`请求失败: ${data}`)
            stopped = true
            return false
          }

          for (const char of data) {
            if (!isCurrent()) return false

            while (isPaused.value) {
              updateAi({ status: 'paused' })
              await sleep(50)
              if (!isCurrent()) return false
            }

            streamingContent.value += char
            updateAi({ content: streamingContent.value, status: 'streaming' })
            if (isAtBottom.value) requestScroll()
            await sleep(typingSpeed.value)
          }
        }
        return true
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        pendingBuffer += decoder.decode(value, { stream: true })
        const { events, rest } = parseSseEvents(pendingBuffer)
        pendingBuffer = rest

        if (!(await consume(events))) {
          try { await reader.cancel() } catch (e) { /* 已关闭 */ }
          break
        }
      }

      // 服务端未以空行结束的尾事件兜底
      if (!stopped && pendingBuffer.trim()) {
        const { events } = parseSseEvents(pendingBuffer + '\n\n')
        await consume(events)
      }

      if (isCurrent()) updateConversationInList()
    } catch (error) {
      if (error.name === 'AbortError') {
        console.info('流式请求已被主动取消')
        return
      }
      console.error('流式发送失败:', error)
      // 已有部分内容时保留，不再整条移除
      const idx = findIndexById(aiId)
      const hasContent = idx !== -1 && Boolean(messages.value[idx].content)
      ElMessage.error(error.userMessage || (hasContent
        ? '回答未完整返回，已保留已生成内容'
        : '发送失败，请检查网络连接'))
    } finally {
      if (sequence === streamSequence) {
        isStreaming.value = false
        isPaused.value = false
        currentStreamController.value = null
        settleActiveMessage()
        if (scrollFrame) {
          cancelAnimationFrame(scrollFrame)
          scrollFrame = null
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
