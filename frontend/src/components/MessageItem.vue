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
        <template v-else>{{ msg.content }}</template>
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
defineProps({
  msg: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  formattedTime: { type: String, default: '' }
})
defineEmits(['contextmenu', 'toggle-select', 'toggle-pause'])
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
