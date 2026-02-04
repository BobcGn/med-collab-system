<template>
  <div class="user-info-card">
    <h3>用户信息</h3>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="user" class="user-details">
      <div class="info-row">
        <span class="label">用户ID:</span>
        <span class="value">{{ user.id }}</span>
      </div>
      <div class="info-row">
        <span class="label">所属医院:</span>
        <span class="value">{{ user.hospitalName || user.hospitalId || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="label">所属科室:</span>
        <span class="value">{{ user.deptName || user.deptCode || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="label">用户序号:</span>
        <span class="value">{{ user.userSeq }}</span>
      </div>
      <div class="info-row">
        <span class="label">用户名:</span>
        <span class="value">{{ user.username || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="label">真实姓名:</span>
        <span class="value">{{ user.fullName }}</span>
      </div>
      <div class="info-row">
        <span class="label">角色:</span>
        <span class="value role-badge" :class="user.role">{{ user.role }}</span>
      </div>
      <div class="info-row">
        <span class="label">创建时间:</span>
        <span class="value">{{ formatDate(user.createdAt) }}</span>
      </div>
      <div v-if="user.updatedAt" class="info-row">
        <span class="label">更新时间:</span>
        <span class="value">{{ formatDate(user.updatedAt) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../utils/api.js'

const props = defineProps({
  userId: {
    type: String,
    required: true,
  },
})

const user = ref(null)
const loading = ref(false)
const error = ref('')

const fetchUserInfo = async () => {
  loading.value = true
  error.value = ''

  try {
    user.value = await authApi.getUserById(props.userId)
  } catch (err) {
    error.value = err.message || '获取用户信息失败'
  } finally {
    loading.value = false
  }
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUserInfo()
})
</script>

<style scoped>
.user-info-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.user-info-card h3 {
  margin: 0 0 1rem 0;
  color: #333;
  font-size: 1.25rem;
}

.loading, .error {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.error {
  color: #e74c3c;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  font-weight: 500;
  color: #666;
}

.value {
  color: #333;
}

.role-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 500;
}

.role-badge.admin {
  background: #e8f5e9;
  color: #2e7d32;
}

.role-badge.doctor {
  background: #e3f2fd;
  color: #1565c0;
}

.role-badge.nurse {
  background: #fce4ec;
  color: #c2185b;
}
</style>
