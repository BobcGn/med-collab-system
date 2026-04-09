<template>
  <div class="notifications-page">
    <div class="page-header">
      <div>
        <h2>消息通知</h2>
        <p>展示来自系统公告通道的实时通知，打开本页后自动标记为已读。</p>
      </div>
      <span class="status-badge" :class="connectionStatus">{{ statusText }}</span>
    </div>

    <section class="summary-strip">
      <div class="summary-card">
        <span class="summary-label">总通知数</span>
        <strong>{{ notifications.length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">未读数</span>
        <strong>{{ unreadCount }}</strong>
      </div>
    </section>

    <section class="notification-list-card">
      <div v-if="notifications.length === 0" class="empty-state">
        当前没有可展示的系统通知。
      </div>

      <div v-else class="notification-list">
        <article v-for="item in notifications" :key="item.id" class="notification-item">
          <div class="item-top">
            <span class="priority-tag" :class="item.priority">{{ priorityLabel(item.priority) }}</span>
            <span>{{ formatTime(item.createdAt) }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.content }}</p>
          <div class="item-footer">发送人：{{ item.senderName }}</div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { connectNotificationSocket } from '../utils/ws.js'
import {
  getUnreadNotificationCount,
  loadSystemNotifications,
  markSystemNotificationsRead,
  mergeSystemNotification,
  replaceSystemNotifications,
  systemNotificationEvents,
} from '../utils/systemNotifications.js'

const notifications = ref(loadSystemNotifications())
const connectionStatus = ref('connecting')
const socketRef = ref(null)

const unreadCount = computed(() => getUnreadNotificationCount())
const statusText = computed(() => {
  const statusMap = {
    connecting: '连接中',
    connected: '已连接',
    disconnected: '已断开',
    error: '连接异常',
  }
  return statusMap[connectionStatus.value] || '未知状态'
})

const syncNotifications = () => {
  notifications.value = loadSystemNotifications()
}

const connectSocket = () => {
  const socket = connectNotificationSocket()
  socketRef.value = socket
  connectionStatus.value = 'connecting'

  socket.onopen = () => {
    connectionStatus.value = 'connected'
  }

  socket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data)
      if (payload.type === 'snapshot') {
        notifications.value = replaceSystemNotifications(payload.notifications || [])
      } else if (payload.type === 'announcement' && payload.notification) {
        notifications.value = mergeSystemNotification(payload.notification)
      }
    } catch (_error) {
      connectionStatus.value = 'error'
    }
  }

  socket.onclose = () => {
    connectionStatus.value = 'disconnected'
  }

  socket.onerror = () => {
    connectionStatus.value = 'error'
  }
}

const priorityLabel = (priority) => {
  const labelMap = {
    normal: '普通',
    important: '重要',
    urgent: '紧急',
  }
  return labelMap[priority] || '普通'
}

const formatTime = (value) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  markSystemNotificationsRead()
  syncNotifications()
  connectSocket()
  window.addEventListener(systemNotificationEvents.update, syncNotifications)
})

onUnmounted(() => {
  window.removeEventListener(systemNotificationEvents.update, syncNotifications)
  if (socketRef.value) {
    socketRef.value.close()
  }
})
</script>

<style scoped>
.notifications-page {
  display: grid;
  gap: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}

.page-header h2 {
  margin: 0 0 0.35rem;
}

.page-header p {
  margin: 0;
  color: #5f6c7b;
}

.status-badge {
  padding: 0.55rem 0.95rem;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
}

.status-badge.connecting {
  background: #fff1c2;
  color: #7a5c00;
}

.status-badge.connected {
  background: #dff5e3;
  color: #17663a;
}

.status-badge.disconnected,
.status-badge.error {
  background: #ffe1de;
  color: #9c2f24;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.summary-card,
.notification-list-card {
  background: #fff;
  border-radius: 16px;
  padding: 1.35rem;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.08);
}

.summary-card strong {
  display: block;
  margin-top: 0.5rem;
  font-size: 1.8rem;
  color: #1d4ed8;
}

.summary-label {
  color: #64748b;
  font-size: 0.92rem;
}

.notification-list {
  display: grid;
  gap: 1rem;
}

.notification-item {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 1rem 1.1rem;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.item-top,
.item-footer {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  color: #64748b;
  font-size: 0.85rem;
}

.notification-item h3 {
  margin: 0.65rem 0 0.45rem;
  color: #1e293b;
}

.notification-item p {
  margin: 0;
  color: #475569;
  line-height: 1.65;
}

.item-footer {
  margin-top: 0.85rem;
}

.priority-tag {
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  font-weight: 600;
}

.priority-tag.normal {
  background: #e2e8f0;
  color: #334155;
}

.priority-tag.important {
  background: #fde68a;
  color: #92400e;
}

.priority-tag.urgent {
  background: #fecaca;
  color: #991b1b;
}

.empty-state {
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 2rem 1rem;
  text-align: center;
  color: #64748b;
  background: #f8fafc;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
