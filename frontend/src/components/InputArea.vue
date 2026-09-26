<template>
  <div class="input-area">
    <el-input
      :model-value="modelValue"
      placeholder="输入消息..."
      :disabled="disabled"
      @update:model-value="(val) => $emit('update:modelValue', val)"
      @keyup.enter="$emit('send')"
    />
    <el-button
      type="primary"
      :loading="disabled"
      :disabled="!modelValue.trim() || disabled"
      @click="$emit('send')"
    >
      <el-icon><Promotion /></el-icon>
      {{ disabled ? '生成中...' : '发送' }}
    </el-button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
defineEmits(['update:modelValue', 'send'])
</script>

<style scoped>
.input-area {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e8e8e8;
  display: flex;
  gap: 12px;
}
.input-area :deep(.el-input) {
  flex: 1;
}
</style>
