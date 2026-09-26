<template>
  <div class="conversation-list" :class="{ collapsed: isCollapsed }">
    <div class="list-header">
      <div class="header-left">
        <el-link
          type="primary"
          :underline="false"
          class="collapse-btn"
          @click="$emit('toggle-collapse')"
        >
          <el-icon><ArrowLeft /></el-icon>
        </el-link>
        <h2 v-show="!isCollapsed">对话列表</h2>
      </div>
      <el-button type="primary" size="small" @click="$emit('create')">
        <el-icon><Plus /></el-icon>
        <span v-show="!isCollapsed">新建会话</span>
      </el-button>
    </div>

    <div class="conversations">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: selectedConversation === conv.id }"
        @click="$emit('select', conv.id)"
      >
        <div class="conv-info">
          <el-icon :size="20" color="#667eea" class="conv-icon"><ChatLineRound /></el-icon>
          <div class="conv-content">
            <div class="conv-title">{{ conv.title }}</div>
            <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
          </div>
        </div>
        <el-link
          type="danger"
          :underline="false"
          class="delete-btn"
          @click.stop="$emit('delete', conv.id)"
        >
          <el-icon><Delete /></el-icon>
        </el-link>
      </div>

      <div v-if="conversations.length === 0" class="empty-state">
        <el-icon :size="48" color="#ccc">
          <ChatLineRound />
        </el-icon>
        <p v-show="!isCollapsed">暂无对话</p>
        <el-button v-show="!isCollapsed" type="primary" size="small" @click="$emit('create')">
          开始新对话
        </el-button>
      </div>
    </div>

    <div class="user-info" v-show="!isCollapsed">
      <div class="user-avatar">
        <el-icon :size="32" color="#667eea"><User /></el-icon>
      </div>
      <div class="user-details">
        <div class="user-name">@{{ currentUser }}</div>
        <div class="user-status">
          <span class="status-dot online"></span>
          <span>在线</span>
        </div>
      </div>
      <el-link
        type="primary"
        :underline="false"
        class="logout-btn"
        @click="$emit('logout')"
      >
        <el-icon><WindPower /></el-icon>
      </el-link>
    </div>
  </div>
</template>

<script setup>
defineProps({
  conversations: { type: Array, default: () => [] },
  selectedConversation: { type: [Number, String, null], default: null },
  isCollapsed: { type: Boolean, default: false },
  currentUser: { type: String, default: '用户' }
})
defineEmits(['select', 'create', 'delete', 'toggle-collapse', 'logout'])

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString()
}
</script>

<style scoped>
.conversation-list {
  width: 320px;
  background: white;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, padding 0.3s ease, border 0.3s ease;
  flex-shrink: 0;
  overflow: hidden;
}
.conversation-list.collapsed {
  width: 0;
  padding: 0;
  border-right: none;
}
.list-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.collapse-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: background-color 0.2s;
}
.collapse-btn:hover {
  background-color: #f5f5f5;
}
.conversation-list.collapsed .collapse-btn {
  transform: rotate(180deg);
}
.list-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.conversations {
  flex: 1;
  overflow-y: auto;
}
.conversation-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: background-color 0.2s;
}
.conversation-item:hover {
  background-color: #fafafa;
}
.conversation-item.active {
  background-color: #f0f5ff;
}
.conv-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.conv-icon { flex-shrink: 0; }
.conv-content { flex: 1; min-width: 0; }
.conv-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-time {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}
.delete-btn {
  opacity: 0;
  color: #999;
  transition: opacity 0.2s;
}
.conversation-item:hover .delete-btn {
  opacity: 1;
}
.delete-btn:hover {
  color: #f56c6c;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
}
.empty-state p {
  margin: 16px 0;
}
.user-info {
  padding: 16px;
  border-top: 1px solid #e8e8e8;
  background: linear-gradient(135deg, #f8f9ff 0%, #f0f5ff 100%);
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-details {
  flex: 1;
  min-width: 0;
}
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #67c23a;
  margin-top: 2px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #999;
}
.status-dot.online {
  background-color: #67c23a;
}
.logout-btn {
  color: #999;
  transition: color 0.2s;
}
.logout-btn:hover {
  color: #f56c6c;
}
</style>
