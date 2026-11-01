<template>
  <Transition name="menu">
    <div
      v-if="visible"
      class="context-menu"
      :style="{ left: position.x + 'px', top: position.y + 'px' }"
      role="menu"
      @click.stop
    >
      <button type="button" class="context-menu-item" role="menuitem" @click="$emit('select')">
        <el-icon><Check /></el-icon>
        <span>{{ isSelected ? '取消选择' : '选择消息' }}</span>
      </button>
      <div class="context-menu-divider"></div>
      <button
        type="button"
        class="context-menu-item is-danger"
        role="menuitem"
        @click="$emit('delete')"
      >
        <el-icon><Delete /></el-icon>
        <span>删除消息</span>
      </button>
    </div>
  </Transition>
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
  z-index: 1000;
  width: 172px;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(16px);
  box-shadow: var(--shadow-lg);
  transform-origin: top left;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12.5px;
  color: var(--ink-700);
  text-align: left;
  transition:
    background-color var(--dur) var(--ease-soft),
    color var(--dur) var(--ease-soft);
}

.context-menu-item :deep(.el-icon) {
  font-size: 13px;
  color: var(--ink-400);
  transition: color var(--dur) var(--ease-soft);
}

.context-menu-item:hover {
  background: var(--surface-tint);
  color: var(--ink-900);
}

.context-menu-item.is-danger {
  color: var(--danger);
}

.context-menu-item.is-danger :deep(.el-icon) {
  color: var(--danger);
  opacity: 0.75;
}

.context-menu-item.is-danger:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

.context-menu-divider {
  height: 1px;
  margin: 4px 8px;
  background: var(--line);
}

.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 160ms var(--ease-soft),
    transform 200ms var(--ease-out);
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
}
</style>
