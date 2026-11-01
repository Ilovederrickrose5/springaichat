<template>
  <article
    class="message-item"
    :class="[
      msg.role === 'user' ? 'user-message' : 'ai-message',
      { selected }
    ]"
    @contextmenu="(e) => $emit('contextmenu', e, msg)"
  >
    <div v-if="isPersisted" class="message-checkbox">
      <el-checkbox
        :checked="selected"
        aria-label="选择消息"
        @change="(val) => $emit('toggle-select', msg.id, val)"
      />
    </div>

    <div class="message-avatar" :class="msg.role">
      <BrandMark v-if="msg.role !== 'user'" size="sm" />
      <span v-else class="avatar-initial">{{ userInitial }}</span>
    </div>

    <div class="message-content">
      <div class="bubble">
        <div v-if="msg.status === 'thinking' && !msg.content" class="thinking-indicator">
          <span class="thinking-dot"></span>
          <span class="thinking-dot"></span>
          <span class="thinking-dot"></span>
          <span class="thinking-text">正在思考</span>
        </div>
        <div v-else class="message-text">
          <div class="markdown-body" v-html="renderedHtml"></div>
        </div>
      </div>

      <div class="message-footer">
        <span v-if="msg.status === 'streaming'" class="streaming-hint">
          <i class="status-dot"></i>正在生成
        </span>
        <span v-else-if="msg.status === 'paused'" class="streaming-hint is-paused">
          <i class="status-dot"></i>已暂停
        </span>
        <span class="message-time">{{ formattedTime }}</span>
        <button
          v-if="msg.role === 'assistant' && (msg.status === 'streaming' || msg.status === 'paused')"
          type="button"
          class="pause-btn"
          @click.stop="$emit('toggle-pause', msg)"
        >
          <el-icon>
            <VideoPause v-if="msg.status === 'streaming'" />
            <VideoPlay v-else />
          </el-icon>
          {{ msg.status === 'streaming' ? '暂停' : '继续' }}
        </button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github.css'
import BrandMark from '@/components/BrandMark.vue'

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

const userInitial = computed(() => {
  const name = (localStorage.getItem('username') || '我').trim()
  return name.charAt(0).toUpperCase()
})

// 本地临时消息（流式中的 user/assistant）没有数据库 id，不参与批量选择
const isPersisted = computed(() => typeof props.msg.id === 'number')

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]))
}

// 流式期间实时解析 Markdown（节流渲染，避免逐字符解析拖慢打字机）
const STREAM_RENDER_INTERVAL = 80
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
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 26px;
  padding: 6px 8px;
  border-radius: 16px;
  transition: background-color var(--dur) var(--ease-soft);
  animation: fade-up 420ms var(--ease-out) both;
}

.message-item:hover {
  background: rgba(27, 30, 36, 0.022);
}

.message-item.selected {
  background: rgba(63, 91, 116, 0.055);
  box-shadow: inset 0 0 0 1px rgba(63, 91, 116, 0.16);
}

.user-message {
  flex-direction: row-reverse;
}

/* ---------- 选择框 ---------- */
.message-checkbox {
  display: flex;
  align-items: center;
  align-self: center;
  width: 20px;
  flex-shrink: 0;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--dur) var(--ease-soft);
}

.message-item:hover .message-checkbox,
.message-item:focus-within .message-checkbox,
.message-item.selected .message-checkbox {
  opacity: 1;
  pointer-events: auto;
}

.message-checkbox :deep(.el-checkbox) {
  height: auto;
}

.message-checkbox :deep(.el-checkbox__inner) {
  width: 15px;
  height: 15px;
  border-radius: 5px;
}

.message-checkbox :deep(.el-checkbox__label) {
  display: none;
}

/* ---------- 头像 ---------- */
.message-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  flex-shrink: 0;
}

.avatar-initial {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--warm-soft);
  color: #7a5836;
  font-size: 12px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(122, 88, 54, 0.08);
}

/* ---------- 内容 ---------- */
.message-content {
  display: flex;
  flex-direction: column;
  max-width: min(700px, 82%);
  min-width: 0;
}

.user-message .message-content {
  align-items: flex-end;
}

.bubble {
  position: relative;
  padding: 13px 17px;
  font-size: 14px;
  line-height: 1.72;
  word-break: break-word;
  transition:
    box-shadow var(--dur) var(--ease-soft),
    border-color var(--dur) var(--ease-soft);
}

.ai-message .bubble {
  border: 1px solid var(--line);
  border-radius: 6px 18px 18px 18px;
  background: var(--surface);
  color: var(--ink-800);
  box-shadow: var(--shadow-sm);
}

.ai-message:hover .bubble {
  border-color: rgba(27, 30, 36, 0.1);
  box-shadow: var(--shadow-md);
}

.user-message .bubble {
  border-radius: 18px 6px 18px 18px;
  background: linear-gradient(160deg, #2a2e37 0%, #1d2027 100%);
  color: #f4f3ef;
  box-shadow:
    0 10px 24px -18px rgba(27, 30, 36, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.user-message .message-text {
  color: #f4f3ef;
}

.message-text {
  min-width: 0;
}

/* ---------- 思考态 ---------- */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 0;
}

.thinking-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-400);
  animation: breathe 1.5s var(--ease-soft) infinite;
}

.thinking-dot:nth-child(2) {
  animation-delay: 180ms;
}

.thinking-dot:nth-child(3) {
  animation-delay: 360ms;
}

.thinking-text {
  margin-left: 7px;
  font-size: 12.5px;
  color: var(--ink-400);
}

/* ---------- 底部信息 ---------- */
.message-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 7px;
  padding: 0 3px;
  min-height: 22px;
}

.user-message .message-footer {
  flex-direction: row-reverse;
}

.message-time {
  font-size: 11px;
  color: var(--ink-400);
  letter-spacing: 0.01em;
}

.streaming-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--ink-400);
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse-dot 1.6s var(--ease-soft) infinite;
}

.streaming-hint.is-paused .status-dot {
  background: var(--warm);
  animation: none;
  opacity: 0.8;
}

.pause-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--surface);
  font-size: 11px;
  color: var(--ink-500);
  box-shadow: var(--shadow-xs);
  transition:
    color var(--dur) var(--ease-soft),
    border-color var(--dur) var(--ease-soft),
    background-color var(--dur) var(--ease-soft);
}

.pause-btn:hover {
  color: var(--ink-900);
  border-color: var(--line-strong);
  background: var(--surface-soft);
}

.pause-btn :deep(.el-icon) {
  font-size: 12px;
}

/* ---------- Markdown ---------- */
.message-text :deep(.markdown-body > *:first-child) {
  margin-top: 0;
}

.message-text :deep(.markdown-body > *:last-child) {
  margin-bottom: 0;
}

.message-text :deep(.markdown-body p) {
  margin: 0 0 10px;
}

.message-text :deep(.markdown-body h1),
.message-text :deep(.markdown-body h2),
.message-text :deep(.markdown-body h3),
.message-text :deep(.markdown-body h4),
.message-text :deep(.markdown-body h5),
.message-text :deep(.markdown-body h6) {
  margin: 16px 0 7px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: -0.01em;
  color: var(--ink-900);
}

.message-text :deep(.markdown-body h1) { font-size: 18px; }
.message-text :deep(.markdown-body h2) { font-size: 16.5px; }
.message-text :deep(.markdown-body h3) { font-size: 15px; }
.message-text :deep(.markdown-body h4) { font-size: 14px; }
.message-text :deep(.markdown-body h5),
.message-text :deep(.markdown-body h6) { font-size: 13.5px; }

.message-text :deep(.markdown-body ul),
.message-text :deep(.markdown-body ol) {
  margin: 0 0 10px;
  padding-left: 21px;
}

.message-text :deep(.markdown-body li) {
  margin: 3px 0;
}

.message-text :deep(.markdown-body li::marker) {
  color: var(--ink-300);
}

.message-text :deep(.markdown-body strong) {
  font-weight: 600;
  color: var(--ink-900);
}

.message-text :deep(.markdown-body blockquote) {
  margin: 10px 0;
  padding: 8px 14px;
  border-left: 2px solid rgba(63, 91, 116, 0.35);
  border-radius: 0 8px 8px 0;
  background: var(--surface-tint);
  color: var(--ink-500);
  font-size: 13.5px;
}

.message-text :deep(.markdown-body code) {
  font-family: var(--font-mono);
  font-size: 12.5px;
  padding: 1px 5px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--surface-tint);
}

.message-text :deep(.markdown-body pre) {
  margin: 10px 0;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #f7f6f2;
  overflow-x: auto;
  line-height: 1.6;
}

.message-text :deep(.markdown-body pre code) {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 12.5px;
}

.message-text :deep(.markdown-body table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 12.5px;
}

.message-text :deep(.markdown-body th),
.message-text :deep(.markdown-body td) {
  padding: 7px 10px;
  border: 1px solid var(--line);
  text-align: left;
}

.message-text :deep(.markdown-body th) {
  background: var(--surface-tint);
  font-weight: 600;
  color: var(--ink-700);
}

.message-text :deep(.markdown-body a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
  text-decoration-color: rgba(63, 91, 116, 0.35);
}

.message-text :deep(.markdown-body a:hover) {
  color: var(--accent-deep);
  text-decoration-color: currentColor;
}

.message-text :deep(.markdown-body hr) {
  margin: 16px 0;
  border: none;
  border-top: 1px solid var(--line);
}

.message-text :deep(.markdown-body img) {
  max-width: 100%;
  border-radius: 10px;
}

/* ---------- 深色气泡内的图文对比 ---------- */
.user-message :deep(.markdown-body a) {
  color: #cfdce8;
  text-decoration-color: rgba(207, 220, 232, 0.4);
}

.user-message :deep(.markdown-body code) {
  border-color: rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  color: #f4f3ef;
}

.user-message :deep(.markdown-body blockquote) {
  border-left-color: rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(244, 243, 239, 0.72);
}

@media (max-width: 640px) {
  .message-item {
    gap: 9px;
    margin-bottom: 22px;
    padding: 4px 2px;
  }

  .message-content {
    max-width: 88%;
  }

  .bubble {
    padding: 11px 14px;
    font-size: 13.5px;
  }

  .message-checkbox {
    position: absolute;
    top: 10px;
    left: -2px;
    width: 18px;
    z-index: 1;
  }

  .message-checkbox :deep(.el-checkbox__inner) {
    background: var(--surface);
  }

  .user-message .message-checkbox {
    left: auto;
    right: -2px;
  }
}
</style>
