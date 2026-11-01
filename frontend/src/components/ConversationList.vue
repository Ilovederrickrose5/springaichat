<template>
  <aside class="conversation-list" :class="{ collapsed: isCollapsed }">
    <header class="list-header">
      <div class="brand">
        <BrandMark size="sm" />
        <div class="brand-text">
          <strong>企智通</strong>
          <span>Knowledge assistant</span>
        </div>
      </div>
      <button
        type="button"
        class="icon-btn collapse-btn"
        title="收起会话列表"
        aria-label="收起会话列表"
        @click="$emit('toggle-collapse')"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>
    </header>

    <div class="new-chat-row">
      <button type="button" class="new-chat-btn" @click="$emit('create')">
        <el-icon><Plus /></el-icon>
        <span>新建会话</span>
      </button>
    </div>

    <div class="section-head">
      <span>最近对话</span>
      <span class="section-count">{{ conversations.length }}</span>
    </div>

    <nav class="conversations" aria-label="会话列表">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: selectedConversation === conv.id }"
        role="button"
        tabindex="0"
        @click="$emit('select', conv.id)"
        @keydown.enter="$emit('select', conv.id)"
      >
        <div class="conv-body">
          <div class="conv-title">{{ conv.title || '新对话' }}</div>
          <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
        </div>
        <button
          type="button"
          class="conv-delete"
          title="删除会话"
          aria-label="删除会话"
          @click.stop="$emit('delete', conv.id)"
        >
          <el-icon><Delete /></el-icon>
        </button>
      </div>

      <div v-if="conversations.length === 0" class="empty-state">
        <span class="empty-icon">
          <el-icon><ChatLineRound /></el-icon>
        </span>
        <p class="empty-title">还没有对话</p>
        <p class="empty-text">从一个问题开始，知识会慢慢聚起来</p>
        <button type="button" class="text-btn empty-action" @click="$emit('create')">
          新建会话
        </button>
      </div>
    </nav>

    <footer class="user-info">
      <span class="user-avatar">{{ initial }}</span>
      <div class="user-details">
        <div class="user-name">{{ currentUser }}</div>
        <div class="user-status">
          <span class="status-dot"></span>
          已登录
        </div>
      </div>
    </footer>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { formatTime } from '@/utils/time'
import BrandMark from '@/components/BrandMark.vue'

const props = defineProps({
  conversations: { type: Array, default: () => [] },
  selectedConversation: { type: [Number, String, null], default: null },
  isCollapsed: { type: Boolean, default: false },
  currentUser: { type: String, default: '用户' }
})
defineEmits(['select', 'create', 'delete', 'toggle-collapse', 'logout'])

const initial = computed(() => {
  const name = (props.currentUser || '用').trim()
  return name.charAt(0).toUpperCase()
})
</script>

<style scoped>
.conversation-list {
  width: 292px;
  flex-shrink: 0;
  margin-right: 14px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(27, 30, 36, 0.06);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, #ffffff 0%, #fbfaf7 100%);
  box-shadow: var(--shadow-sm);
  transition:
    width 320ms var(--ease-out),
    margin 320ms var(--ease-out),
    opacity 200ms var(--ease-soft);
}

.conversation-list.collapsed {
  width: 0;
  margin-right: 0;
  opacity: 0;
  border-width: 0;
}

/* ---------- 顶部 ---------- */
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 18px 12px 14px 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.brand-text strong {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--ink-900);
  white-space: nowrap;
}

.brand-text span {
  margin-top: 2px;
  font-size: 9px;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--ink-400);
  white-space: nowrap;
}

.collapse-btn {
  flex-shrink: 0;
}

.new-chat-row {
  padding: 0 14px 16px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  height: 40px;
  border-radius: 11px;
  background: linear-gradient(180deg, #2b2f38, #1c1f26);
  color: #f7f6f3;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  box-shadow:
    0 6px 16px -12px rgba(27, 30, 36, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition:
    transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out);
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow:
    0 10px 20px -12px rgba(27, 30, 36, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.new-chat-btn:active {
  transform: translateY(0);
}

.new-chat-btn :deep(.el-icon) {
  font-size: 15px;
}

/* ---------- 会话列表 ---------- */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 8px;
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  color: var(--ink-400);
}

.section-count {
  min-width: 20px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: rgba(27, 30, 36, 0.05);
  font-size: 10px;
  letter-spacing: 0;
  text-align: center;
  color: var(--ink-500);
}

.conversations {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conversation-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 8px 10px 13px;
  border-radius: 12px;
  cursor: pointer;
  transition:
    background-color var(--dur) var(--ease-soft),
    transform var(--dur) var(--ease-out);
}

.conversation-item::before {
  content: "";
  position: absolute;
  left: 3px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  opacity: 0;
  transform: scaleY(0.4);
  transition:
    opacity var(--dur) var(--ease-soft),
    transform var(--dur) var(--ease-out);
}

.conversation-item:hover {
  background: rgba(27, 30, 36, 0.035);
}

.conversation-item.active {
  background: var(--accent-soft);
}

.conversation-item.active::before {
  opacity: 1;
  transform: scaleY(1);
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-700);
  transition: color var(--dur) var(--ease-soft);
}

.conversation-item:hover .conv-title {
  color: var(--ink-900);
}

.conversation-item.active .conv-title {
  color: var(--ink-900);
  font-weight: 600;
}

.conv-time {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-400);
  transition: opacity var(--dur) var(--ease-soft);
}

.conv-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 8px;
  color: var(--ink-400);
  opacity: 0;
  transform: translateX(2px);
  transition:
    opacity var(--dur) var(--ease-soft),
    transform var(--dur) var(--ease-out),
    background-color var(--dur) var(--ease-soft),
    color var(--dur) var(--ease-soft);
}

.conversation-item:hover .conv-delete,
.conv-delete:focus-visible {
  opacity: 1;
  transform: translateX(0);
}

.conv-delete:hover {
  color: var(--danger);
  background: rgba(176, 74, 67, 0.1);
}

/* ---------- 空状态 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 44px 20px 30px;
  text-align: center;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: var(--surface-tint);
  color: var(--ink-300);
  font-size: 20px;
  box-shadow: var(--shadow-inset);
}

.empty-title {
  margin-top: 14px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-700);
}

.empty-text {
  margin-top: 4px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ink-400);
}

.empty-action {
  margin-top: 14px;
  color: var(--accent);
}

/* ---------- 用户区 ---------- */
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--line);
}

.user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--warm-soft);
  color: #7a5836;
  font-size: 13px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(122, 88, 54, 0.08);
}

.user-details {
  flex: 1;
  min-width: 0;
}

.user-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-800);
}

.user-status {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 1px;
  font-size: 11px;
  color: var(--ink-400);
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7d9a7b;
  box-shadow: 0 0 0 3px rgba(125, 154, 123, 0.12);
}

@media (max-width: 820px) {
  .conversation-list {
    position: absolute;
    top: 10px;
    bottom: 10px;
    left: 10px;
    z-index: 20;
    width: 272px;
    margin-right: 0;
    box-shadow: var(--shadow-lg);
  }

  .conversation-list.collapsed {
    transform: translateX(-16px);
    pointer-events: none;
  }
}
</style>
