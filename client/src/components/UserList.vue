<template>
  <div class="user-list-container">
    <div class="header">
      <h2>用户列表</h2>
      <div class="actions">
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="搜索用户..."
          @input="handleSearch"
        />
        <select v-model="roleFilter" @change="fetchUsers">
          <option value="">全部角色</option>
          <option value="admin">管理员</option>
          <option value="doctor">医生</option>
        </select>
        <button @click="fetchUsers">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else>
      <div class="user-grid">
        <div
          v-for="user in users"
          :key="user.id"
          class="user-card"
          @click="selectUser(user)"
        >
          <div class="user-avatar">
            {{ user.fullName.charAt(0) }}
          </div>
          <div class="user-info">
            <div class="user-name">{{ user.fullName }}</div>
            <div class="user-meta">
              <span class="role" :class="user.role">{{ user.role }}</span>
              <span class="username">{{ user.username || '-' }}</span>
            </div>
            <div class="user-details">
              <span>{{ user.hospitalId }}</span>
              <span>{{ user.deptCode }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="pagination.totalPages > 1" class="pagination">
        <button
          :disabled="pagination.page === 0"
          @click="changePage(pagination.page - 1)"
        >
          上一页
        </button>
        <span>第 {{ pagination.page + 1 }} / {{ pagination.totalPages }} 页</span>
        <button
          :disabled="pagination.page >= pagination.totalPages - 1"
          @click="changePage(pagination.page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, defineEmits } from 'vue'
import { authApi } from '../utils/api.js'

const emit = defineEmits(['select-user'])

const users = ref([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const roleFilter = ref('')
const pagination = ref({
  page: 0,
  size: 12,
  totalPages: 1,
  total: 0,
})

const fetchUsers = async () => {
  loading.value = true
  error.value = ''

  try {
    const params = {
      page: pagination.value.page,
      size: pagination.value.size,
    }
    if (roleFilter.value) {
      params.role = roleFilter.value
    }

    const result = await authApi.getAllUsers(params)
    users.value = result.users || result.content || result
    pagination.value.total = result.totalElements || result.total || users.value.length
    pagination.value.totalPages = Math.ceil(pagination.value.total / pagination.value.size)
  } catch (err) {
    error.value = err.message || '获取用户列表失败'
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    fetchUsers()
    return
  }

  loading.value = true
  error.value = ''

  try {
    const results = await authApi.searchUsers(searchKeyword.value)
    users.value = results || results.content || []
    pagination.value.total = users.value.length
    pagination.value.totalPages = 1
  } catch (err) {
    error.value = err.message || '搜索失败'
  } finally {
    loading.value = false
  }
}

const changePage = (newPage) => {
  pagination.value.page = newPage
  fetchUsers()
}

const selectUser = (user) => {
  emit('select-user', user)
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.user-list-container {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.header h2 {
  margin: 0;
  color: #333;
}

.actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.actions input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  width: 200px;
}

.actions select {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.actions button {
  padding: 0.5rem 1rem;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.actions button:hover {
  background: #5568d3;
}

.loading, .error {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.error {
  color: #e74c3c;
}

.user-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.user-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.user-card:hover {
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.2);
}

.user-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: bold;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  color: #333;
  margin-bottom: 0.25rem;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.role {
  padding: 0.125rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.role.admin {
  background: #e8f5e9;
  color: #2e7d32;
}

.role.doctor {
  background: #e3f2fd;
  color: #1565c0;
}

.role.nurse {
  background: #fce4ec;
  color: #c2185b;
}

.username {
  color: #999;
  font-size: 0.875rem;
}

.user-details {
  display: flex;
  gap: 0.5rem;
  color: #666;
  font-size: 0.875rem;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #e0e0e0;
}

.pagination button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.pagination button:hover:not(:disabled) {
  background: #f5f5f5;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination span {
  color: #666;
}
</style>
