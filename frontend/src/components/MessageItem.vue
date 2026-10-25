<template>
  <div
    class="message-item"
    :class="[
      msg.role === 'user' ? 'user-message' : 'ai-message',
      { selected }
    ]"
    @contextmenu="(e) => $emit('contextmenu', e, msg)"
  >
    <div class="message-checkbox">
      <el-checkbox
        :checked="selected"
        @change="(val) => $emit('toggle-select', msg.id, val)"
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
        <div v-else class="markdown-body" v-html="renderedHtml"></div>
      </div>
      <div class="message-footer">
        <span class="message-time">{{ formattedTime }}</span>
        <div
          v-if="msg.role === 'assistant' && (msg.status === 'streaming' || msg.status === 'paused')"
          class="message-controls"
        >
          <el-link
            type="primary"
            :underline="false"
            class="pause-btn"
            @click.stop="$emit('toggle-pause', msg)"
          >
            <el-icon><Pause v-if="msg.status === 'streaming'" /><Play v-else /></el-icon>
            {{ msg.status === 'streaming' ? '暂停' : '继续' }}
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'

marked.use(markedHighlight({
  emptyLangClass: 'hljs',
  langPrefix: 'hljs language-',
  highlight(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  }
}))

const props = defineProps({
  msg: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  formattedTime: { type: String, default: '' }
})
defineEmits(['contextmenu', 'toggle-select', 'toggle-pause'])

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]))
}

// 流式期间实时解析 Markdown（每 50ms 最多渲染一次，避免逐字符解析拖慢打字机）
const STREAM_RENDER_INTERVAL = 50
const renderedHtml = ref('')
let flushTimer = null
let lastFlushAt = 0

function toPlainHtml(text) {
  return escapeHtml(text).replace(/\n/g, '<br>')
}

function toMarkdownHtml(text) {
  return DOMPurify.sanitize(String(marked.parse(text, { gfm: true, breaks: true })))
}

function flushHtml() {
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = null
  }
  const content = props.msg.content || ''
  if (props.msg.role !== 'assistant') {
    renderedHtml.value = toPlainHtml(content)
  } else {
    renderedHtml.value = content ? toMarkdownHtml(content) : ''
  }
  lastFlushAt = Date.now()
}

watch(
  () => [props.msg.role, props.msg.content, props.msg.status],
  () => {
    const content = props.msg.content || ''
    if (props.msg.role !== 'assistant') {
      flushHtml()
      return
    }
    if (!content) {
      if (flushTimer) {
        clearTimeout(flushTimer)
        flushTimer = null
      }
      renderedHtml.value = ''
      return
    }
    // 已完成 / 历史消息：立即渲染最终结果
    if (!['thinking', 'streaming', 'paused'].includes(props.msg.status)) {
      flushHtml()
      return
    }
    const elapsed = Date.now() - lastFlushAt
    if (elapsed >= STREAM_RENDER_INTERVAL) {
      flushHtml()
      return
    }
    if (!flushTimer) {
      flushTimer = setTimeout(flushHtml, STREAM_RENDER_INTERVAL - elapsed)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (flushTimer) clearTimeout(flushTimer)
})
</script>

<style scoped>
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
.message-text :deep(.markdown-body > *:first-child) {
  margin-top: 0;
}
.message-text :deep(.markdown-body > *:last-child) {
  margin-bottom: 0;
}
.message-text :deep(.markdown-body p) {
  margin: 0 0 8px;
}
.message-text :deep(.markdown-body h1),
.message-text :deep(.markdown-body h2),
.message-text :deep(.markdown-body h3),
.message-text :deep(.markdown-body h4) {
  font-weight: 600;
  line-height: 1.35;
  margin: 12px 0 8px;
}
.message-text :deep(.markdown-body h1) { font-size: 18px; }
.message-text :deep(.markdown-body h2) { font-size: 17px; }
.message-text :deep(.markdown-body h3) { font-size: 16px; }
.message-text :deep(.markdown-body h4) { font-size: 15px; }
.message-text :deep(.markdown-body h5),
.message-text :deep(.markdown-body h6) {
  font-weight: 600;
  line-height: 1.35;
  margin: 12px 0 8px;
  font-size: 14px;
}
.message-text :deep(.markdown-body ul),
.message-text :deep(.markdown-body ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}
.message-text :deep(.markdown-body li) {
  margin: 2px 0;
}
.message-text :deep(.markdown-body blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #764ba2;
  background: #f7f5fb;
  color: #555;
}
.message-text :deep(.markdown-body code) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 4px;
  border-radius: 4px;
}
.message-text :deep(.markdown-body pre) {
  margin: 8px 0;
  padding: 10px 12px;
  background: #f6f8fa;
  border-radius: 8px;
  overflow-x: auto;
}
.message-text :deep(.markdown-body pre code) {
  background: transparent;
  padding: 0;
  font-size: 12.5px;
}
.message-text :deep(.markdown-body table) {
  width: 100%;
  margin: 8px 0;
  border-collapse: collapse;
  font-size: 13px;
}
.message-text :deep(.markdown-body th),
.message-text :deep(.markdown-body td) {
  border: 1px solid #e5e5e5;
  padding: 6px 8px;
  text-align: left;
}
.message-text :deep(.markdown-body th) {
  background: #fafafa;
  font-weight: 600;
}
.message-text :deep(.markdown-body a) {
  color: #667eea;
  text-decoration: none;
}
.message-text :deep(.markdown-body a:hover) {
  text-decoration: underline;
}
.message-text :deep(.markdown-body hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 12px 0;
}
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
.thinking-dot:nth-child(1) { animation-delay: -0.32s; }
.thinking-dot:nth-child(2) { animation-delay: -0.16s; }
@keyframes thinking-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
.thinking-text {
  font-size: 14px;
  color: #999;
  margin-left: 4px;
}
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
</style>
