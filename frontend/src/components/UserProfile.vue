<template>
  <div class="profile-container">
    <div class="profile-card">
      <div class="profile-header">
        <div class="avatar">
          {{ user?.fullName?.charAt(0) || 'U' }}
        </div>
        <h2>{{ user?.fullName || '用户' }}</h2>
        <span class="role-badge" :class="user?.role">{{ user?.role || '-' }}</span>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="user">
        <div class="section">
          <h3>基本信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <label>用户账号</label>
              <span>{{ user.username || '-' }}</span>
            </div>
            <div class="info-item">
              <label>所属医院</label>
              <span>{{ user.hospitalName || user.hospitalId || '-' }}</span>
            </div>
            <div class="info-item">
              <label>所属科室</label>
              <span>{{ user.deptName || user.deptCode || '-' }}</span>
            </div>
            <div class="info-item">
              <label>用户序号</label>
              <span>{{ user.userSeq }}</span>
            </div>
            <div class="info-item">
              <label>真实姓名</label>
              <span>{{ user.fullName }}</span>
            </div>
            <div class="info-item">
              <label>创建时间</label>
              <span>{{ formatDate(user.createdAt) }}</span>
            </div>
          </div>
        </div>

        <div class="section">
          <h3>快捷操作</h3>
          <div class="actions">
            <button @click="showChangePassword = true">修改密码</button>
            <button v-if="isAdmin" @click="showChangeUsername = true">修改用户名</button>
          </div>
        </div>
      </div>

      <!-- 修改密码对话框 -->
      <div v-if="showChangePassword" class="modal-overlay" @click="closeModals">
        <div class="modal" @click.stop>
          <h3>修改密码</h3>
          <form @submit.prevent="handlePasswordChange">
            <div class="form-group">
              <label>旧密码</label>
              <input
                v-model="passwordForm.oldPassword"
                type="password"
                required
              />
            </div>
            <div class="form-group">
              <label>新密码</label>
              <input
                v-model="passwordForm.newPassword"
                type="password"
                required
                minlength="6"
              />
            </div>
            <div class="form-group">
              <label>确认新密码</label>
              <input
                v-model="passwordForm.confirmPassword"
                type="password"
                required
              />
            </div>
            <div v-if="formError" class="error-message">{{ formError }}</div>
            <div class="modal-actions">
              <button type="button" @click="closeModals">取消</button>
              <button type="submit" :disabled="formLoading">确认修改</button>
            </div>
          </form>
        </div>
      </div>

      <!-- 修改用户名对话框（仅管理员可见） -->
      <div v-if="isAdmin && showChangeUsername" class="modal-overlay" @click="closeModals">
        <div class="modal" @click.stop>
          <h3>修改用户名</h3>
          <form @submit.prevent="handleUsernameChange">
            <div class="form-group">
              <label>新用户名</label>
              <input
                v-model="usernameForm.newUsername"
                type="text"
                required
              />
            </div>
            <div v-if="formError" class="error-message">{{ formError }}</div>
            <div class="modal-actions">
              <button type="button" @click="closeModals">取消</button>
              <button type="submit" :disabled="formLoading">确认修改</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { authApi } from '../utils/api'
import { authStore } from '../utils/auth'

const user = ref(null)
const loading = ref(false)
const error = ref('')
const formLoading = ref(false)
const formError = ref('')

const showChangePassword = ref(false)
const showChangeUsername = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const usernameForm = ref({
  newUsername: '',
})

// 是否为管理员（仅管理员可以修改用户名）
const isAdmin = computed(() => user.value?.role === 'admin')

const fetchCurrentUser = async () => {
  loading.value = true
  error.value = ''

  try {
    const userData = await authApi.getCurrentUser()
    user.value = userData
    // 更新localStorage中的用户数据，确保包含最新的字段
    authStore.setCurrentUser(userData)
  } catch (err) {
    error.value = err.message || '获取用户信息失败'
  } finally {
    loading.value = false
  }
}

const handlePasswordChange = async () => {
  formError.value = ''

  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    formError.value = '两次输入的密码不一致'
    return
  }

  if (passwordForm.value.newPassword.length < 6) {
    formError.value = '密码长度至少为6位'
    return
  }

  formLoading.value = true

  try {
    await authApi.changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    alert('密码修改成功！')
    closeModals()
  } catch (err) {
    formError.value = err.message || '密码修改失败'
  } finally {
    formLoading.value = false
  }
}

const handleUsernameChange = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    await authApi.changeUsername(usernameForm.value.newUsername)
    await fetchCurrentUser()
    alert('用户名修改成功！')
    closeModals()
  } catch (err) {
    formError.value = err.message || '用户名修改失败'
  } finally {
    formLoading.value = false
  }
}

const closeModals = () => {
  showChangePassword.value = false
  showChangeUsername.value = false
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  }
  usernameForm.value = {
    newUsername: '',
  }
  formError.value = ''
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchCurrentUser()
})
</script>

<style scoped>
.profile-container {
  max-width: 960px;
  margin: 1.5rem auto 0;
  padding: 0 1rem 2rem;
}

.profile-card {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
  overflow: hidden;
}

.profile-header {
  background: linear-gradient(135deg, #4c6fff 0%, #764ba2 100%);
  color: white;
  padding: 2.25rem 2rem 1.75rem;
  text-align: center;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: white;
  color: #667eea;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  font-weight: bold;
  margin: 0 auto 1rem;
}

.profile-header h2 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
}

.role-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.2);
}

.loading, .error {
  text-align: center;
  padding: 3rem;
  color: #666;
}

.error {
  color: #e74c3c;
}

.section {
  padding: 1.75rem 2rem;
  border-bottom: 1px solid #f0f0f0;
}

.section:last-child {
  border-bottom: none;
}

.section h3 {
  margin: 0 0 1rem 0;
  color: #333;
  font-size: 1.125rem;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-item label {
  font-size: 0.875rem;
  color: #666;
  font-weight: 500;
}

.info-item span {
  color: #333;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.actions button {
  padding: 0.6rem 1.4rem;
  background: #f3f4ff;
  color: #4c6fff;
  border: 1px solid #d6dcff;
  border-radius: 999px;
  font-size: 0.875rem;
  cursor: pointer;
  font-weight: 500;
  transition:
    background 0.2s,
    color 0.2s,
    box-shadow 0.2s;
}

.actions button:hover {
  background: #4c6fff;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(76, 111, 255, 0.3);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.modal h3 {
  margin: 0 0 1.5rem 0;
  color: #333;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
}

.form-group input {
  width: 100%;
  padding: 0.6rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
}

.error-message {
  color: #e74c3c;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.modal-actions {
  display: flex;
  gap: 1rem;
  margin-top: 1.5rem;
}

.modal-actions button {
  flex: 1;
  padding: 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.modal-actions button[type="button"] {
  background: #f0f0f0;
  color: #666;
}

.modal-actions button[type="button"]:hover {
  background: #e0e0e0;
}

.modal-actions button[type="submit"] {
  background: #667eea;
  color: white;
}

.modal-actions button[type="submit"]:hover:not(:disabled) {
  background: #5568d3;
}

.modal-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
