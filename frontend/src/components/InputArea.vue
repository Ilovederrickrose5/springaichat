<template>
  <div class="input-area">
    <div class="composer" :class="{ 'is-busy': disabled }">
      <el-input
        class="message-input"
        type="textarea"
        resize="none"
        :autosize="{ minRows: 1, maxRows: 6 }"
        :model-value="modelValue"
        placeholder="输入你的问题，或粘贴一段需要理解的内容"
        :disabled="disabled"
        @update:model-value="(val) => $emit('update:modelValue', val)"
        @keydown="handleKeydown"
      />

      <div class="composer-bar">
        <div class="composer-hints">
          <span class="shortcut-hints">
            <span class="hint"><kbd>Enter</kbd> 发送</span>
            <span class="hint-sep"></span>
            <span class="hint"><kbd>Shift</kbd><span class="plus">+</span><kbd>Enter</kbd> 换行</span>
          </span>
          <span
            v-if="modelValue.length"
            class="char-count"
            :class="{ warn: modelValue.length > 3600 }"
          >
            {{ modelValue.length }} / 4000
          </span>
        </div>

        <button
          type="button"
          class="send-btn"
          :disabled="!modelValue.trim() || disabled"
          aria-label="发送消息"
          @click="$emit('send')"
        >
          <template v-if="!disabled">
            <span>发送</span>
            <el-icon><Promotion /></el-icon>
          </template>
          <span v-else class="send-loading">
            <i v-if="!paused" class="spinner"></i>{{ paused ? '已暂停' : '生成中' }}
          </span>
        </button>
      </div>
    </div>
    <p class="composer-note">内容由 AI 生成，请核对关键信息</p>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  paused: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'send'])

const handleKeydown = (event) => {
  if (event.key !== 'Enter' || event.shiftKey) return
  // 中文输入法候选词确认的 Enter 不能触发发送
  if (event.isComposing || event.keyCode === 229) return

  event.preventDefault()
  if (props.disabled || !props.modelValue.trim()) return
  emit('send')
}
</script>

<style scoped>
.input-area {
  padding: 10px 24px 14px;
  flex-shrink: 0;
  background: linear-gradient(180deg, rgba(251, 250, 248, 0) 0%, var(--surface-soft) 38%);
}

.composer {
  max-width: 780px;
  margin: 0 auto;
  border: 1px solid var(--line-strong);
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #fdfcfa 100%);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition:
    border-color var(--dur) var(--ease-soft),
    box-shadow var(--dur) var(--ease-soft),
    background-color var(--dur) var(--ease-soft);
}

.composer:focus-within {
  border-color: rgba(63, 91, 116, 0.4);
  background: #fff;
  box-shadow:
    0 0 0 4px var(--accent-wash),
    var(--shadow-md);
}

.composer.is-busy {
  background: var(--surface-soft);
}

.input-area :deep(.el-textarea__inner) {
  padding: 13px 16px 4px;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink-900);
  resize: none;
}

.input-area :deep(.el-textarea__inner::placeholder) {
  color: var(--ink-300);
}

.input-area :deep(.el-textarea.is-disabled .el-textarea__inner) {
  background: transparent;
  color: var(--ink-400);
  cursor: default;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 8px 8px 14px;
}

.composer-hints {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.shortcut-hints {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 10.5px;
  color: var(--ink-400);
  white-space: nowrap;
}

.hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.hint kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border: 1px solid var(--line-strong);
  border-bottom-width: 2px;
  border-radius: 5px;
  background: var(--surface);
  font-family: var(--font-sans);
  font-size: 9.5px;
  color: var(--ink-500);
}

.hint-sep {
  width: 1px;
  height: 10px;
  background: var(--line-strong);
}

.plus {
  margin: 0 1px;
  color: var(--ink-300);
}

.char-count {
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-300);
  white-space: nowrap;
  transition: color var(--dur) var(--ease-soft);
}

.char-count.warn {
  color: var(--danger);
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 74px;
  height: 32px;
  padding: 0 13px;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--ink-900);
  color: #f7f6f3;
  font-size: 12.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  box-shadow:
    0 4px 10px -6px rgba(27, 30, 36, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition:
    background-color var(--dur) var(--ease-soft),
    transform var(--dur) var(--ease-out),
    box-shadow var(--dur) var(--ease-out),
    color var(--dur) var(--ease-soft);
}

.send-btn:hover:not(:disabled) {
  background: #2f333c;
  transform: translateY(-1px);
  box-shadow:
    0 7px 16px -8px rgba(27, 30, 36, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.16);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0);
}

.send-btn:disabled {
  background: var(--surface-tint);
  color: var(--ink-300);
  box-shadow: none;
  cursor: default;
}

.send-btn :deep(.el-icon) {
  font-size: 13px;
}

.send-loading {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(27, 30, 36, 0.16);
  border-top-color: var(--ink-400);
  border-radius: 50%;
  animation: spin 800ms linear infinite;
}

.composer-note {
  max-width: 780px;
  margin: 7px auto 0;
  text-align: center;
  font-size: 10.5px;
  color: var(--ink-300);
  letter-spacing: 0.01em;
}

@media (max-width: 640px) {
  .input-area {
    padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  }

  .composer-bar {
    padding: 2px 7px 7px 12px;
  }

  .shortcut-hints {
    display: none;
  }

  .composer-note {
    display: none;
  }
}
</style>
