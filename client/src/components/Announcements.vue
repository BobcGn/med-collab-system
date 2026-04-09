<template>
  <div class="announcements-page">
    <div class="page-header">
      <div>
        <h2>系统公告</h2>
        <p>管理员通过 Auth WebSocket 发布，所有在线用户实时接收。</p>
      </div>
      <span class="status-badge" :class="connectionStatus">{{ statusText }}</span>
    </div>

    <section v-if="isAdmin" class="publish-card">
      <div class="card-header">
        <h3>发布新公告</h3>
        <span>仅管理员可发送</span>
      </div>

      <div class="form-grid">
        <input v-model.trim="draft.title" type="text" placeholder="公告标题" class="text-input" />
        <select v-model="draft.priority" class="select-input">
          <option value="normal">普通</option>
          <option value="important">重要</option>
          <option value="urgent">紧急</option>
        </select>
      </div>

      <textarea
        v-model.trim="draft.content"
        rows="5"
        class="text-area"
        placeholder="输入要广播给系统用户的通知内容"
      />

      <div class="actions">
        <button class="primary-button" :disabled="!canPublish" @click="publishAnnouncement">
          发布公告
        </button>
      </div>
    </section>

    <section v-else class="info-card">
      <h3>当前账号为只读模式</h3>
      <p>你可以查看实时公告和历史公告；管理员账号可在此页发布系统通知。</p>
    </section>

    <section class="timeline-card">
      <div class="card-header">
        <h3>公告流</h3>
        <span>{{ announcements.length }} 条</span>
      </div>

      <div v-if="announcements.length === 0" class="empty-state">
        暂无系统公告，连接建立后会自动同步历史消息。
      </div>

      <div v-else class="timeline">
        <article v-for="item in announcements" :key="item.id" class="timeline-item">
          <div class="timeline-meta">
            <span class="priority-tag" :class="item.priority">{{ priorityLabel(item.priority) }}</span>
            <span>{{ formatTime(item.createdAt) }}</span>
            <span>发布人：{{ item.senderName }}</span>
          </div>
          <h4>{{ item.title }}</h4>
          <p>{{ item.content }}</p>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { authStore } from '../utils/auth.js'
import { connectNotificationSocket } from '../utils/ws.js'
import {
  loadSystemNotifications,
  mergeSystemNotification,
  replaceSystemNotifications,
  systemNotificationEvents,
} from '../utils/systemNotifications.js'

const announcements = ref(loadSystemNotifications())
const connectionStatus = ref('connecting')
const socketRef = ref(null)
const draft = reactive({
  title: '',
  content: '',
  priority: 'normal',
})

const isAdmin = computed(() => authStore.hasRole('admin'))
const statusText = computed(() => {
  const statusMap = {
    connecting: '连接中',
    connected: '已连接',
    disconnected: '已断开',
    error: '连接异常',
  }
  return statusMap[connectionStatus.value] || '未知状态'
})

const canPublish = computed(() => {
  return isAdmin.value &&
    connectionStatus.value === 'connected' &&
    draft.title.length > 0 &&
    draft.content.length > 0
})

const syncAnnouncements = () => {
  announcements.value = loadSystemNotifications()
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
        announcements.value = replaceSystemNotifications(payload.notifications || [])
      } else if (payload.type === 'announcement' && payload.notification) {
        announcements.value = mergeSystemNotification(payload.notification)
      } else if (payload.type === 'error') {
        connectionStatus.value = 'error'
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

const publishAnnouncement = () => {
  if (!canPublish.value || !socketRef.value) {
    return
  }

  socketRef.value.send(JSON.stringify({
    type: 'publish',
    title: draft.title,
    content: draft.content,
    priority: draft.priority,
  }))

  draft.title = ''
  draft.content = ''
  draft.priority = 'normal'
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
  connectSocket()
  window.addEventListener(systemNotificationEvents.update, syncAnnouncements)
})

onUnmounted(() => {
  window.removeEventListener(systemNotificationEvents.update, syncAnnouncements)
  if (socketRef.value) {
    socketRef.value.close()
  }
})
</script>

<style scoped>
.announcements-page {
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
  color: #213547;
}

.page-header p {
  margin: 0;
  color: #5b6470;
}

.status-badge {
  padding: 0.55rem 0.95rem;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 600;
  white-space: nowrap;
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

.publish-card,
.timeline-card,
.info-card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.card-header h3 {
  margin: 0;
}

.card-header span,
.info-card p {
  color: #687385;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(180px, 1fr);
  gap: 1rem;
  margin-bottom: 1rem;
}

.text-input,
.select-input,
.text-area {
  width: 100%;
  border: 1px solid #d7dde8;
  border-radius: 12px;
  padding: 0.85rem 1rem;
  font-size: 0.95rem;
  color: #24364b;
  background: #f8fafc;
}

.text-input:focus,
.select-input:focus,
.text-area:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.text-area {
  resize: vertical;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #0f766e);
  color: #fff;
  padding: 0.85rem 1.5rem;
  font-weight: 600;
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-state {
  border: 1px dashed #cfd8e3;
  border-radius: 14px;
  padding: 2rem 1rem;
  color: #687385;
  text-align: center;
  background: #f8fafc;
}

.timeline {
  display: grid;
  gap: 1rem;
}

.timeline-item {
  border: 1px solid #e5ebf2;
  border-radius: 14px;
  padding: 1rem 1.1rem;
  background: linear-gradient(180deg, #ffffff, #f9fbff);
}

.timeline-item h4 {
  margin: 0.5rem 0;
  color: #1e293b;
}

.timeline-item p {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
  color: #64748b;
  font-size: 0.85rem;
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

@media (max-width: 768px) {
  .page-header,
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .actions {
    justify-content: stretch;
  }

  .primary-button {
    width: 100%;
  }
}
</style>
