<template>
  <div
    v-if="visible"
    class="context-menu"
    :style="{ left: position.x + 'px', top: position.y + 'px' }"
    @click.stop
  >
    <div class="context-menu-item" @click="$emit('select')">
      <el-icon><Check /></el-icon>
      {{ isSelected ? '取消选择' : '选择' }}
    </div>
    <div class="context-menu-divider"></div>
    <div class="context-menu-item danger" @click="$emit('delete')">
      <el-icon><Delete /></el-icon>
      删除消息
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  position: { type: Object, default: () => ({ x: 0, y: 0 }) },
  isSelected: { type: Boolean, default: false }
})
defineEmits(['select', 'delete'])
</script>

<style scoped>
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
