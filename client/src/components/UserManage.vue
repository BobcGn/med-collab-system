<template>
  <div class="user-manage-container">
    <!-- 导航栏 -->
    <div class="nav-bar">
      <h2>用户管理</h2>
    </div>

    <!-- 用户管理 -->
    <div class="tab-content">
      <div class="toolbar">
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
          <button @click="showAddUser = true" class="btn-primary">添加用户</button>
          <button @click="fetchUsers">刷新</button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="content">
        <div class="table-container">
          <table class="user-table">
            <thead>
              <tr>
                <th>真实姓名</th>
                <th>用户名</th>
                <th>角色</th>
                <th>所属医院</th>
                <th>所属科室</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.fullName }}</td>
                <td>{{ user.username || '-' }}</td>
                <td>
                  <span class="role-badge" :class="user.role">{{ user.role }}</span>
                </td>
                <td>{{ user.hospitalName || user.hospitalId || '-' }}</td>
                <td>{{ user.deptName || user.deptCode || '-' }}</td>
                <td>{{ formatDate(user.createdAt) }}</td>
                <td class="actions">
                  <button @click="viewUser(user)" class="btn-small">查看</button>
                  <button @click="editUser(user)" class="btn-small btn-primary">编辑</button>
                  <button @click="showResetPassword(user)" class="btn-small btn-warning">重置密码</button>
                  <button @click="confirmDelete(user)" class="btn-small btn-danger">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button
            :disabled="pagination.page === 0"
            @click="changePage(pagination.page - 1)"
          >
            上一页
          </button>
          <span>第 {{ pagination.page + 1 }} / {{ pagination.totalPages }} 页</span>
          <span>共 {{ pagination.total }} 条</span>
          <button
            :disabled="pagination.page >= pagination.totalPages - 1"
            @click="changePage(pagination.page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑用户对话框 -->
    <div v-if="showAddUser || showEditUser" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditUser ? '编辑用户' : '添加用户' }}</h3>
        <form @submit.prevent="handleUserSubmit">
          <!-- 编辑模式：显示医院和科室信息但不允许修改 -->
          <div v-if="showEditUser" class="form-group">
            <label>所属医院</label>
            <input
              :value="userForm.hospitalId"
              type="text"
              disabled
              class="form-input-disabled"
            />
          </div>
          <div v-if="showEditUser" class="form-group">
            <label>所属科室</label>
            <input
              :value="userForm.deptCode"
              type="text"
              disabled
              class="form-input-disabled"
            />
          </div>
          <!-- 添加模式：可以选择医院和科室 -->
          <template v-else>
            <div class="form-group">
              <label>所属医院 *</label>
              <select
                v-model="userForm.hospitalId"
                required
                @change="handleHospitalChange"
                class="form-select"
              >
                <option value="">请选择医院</option>
                <option v-for="hospital in hospitals" :key="hospital.id" :value="hospital.id">
                  {{ hospital.id }} - {{ hospital.name }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>所属科室 *</label>
              <select
                v-model="userForm.deptCode"
                required
                :disabled="!userForm.hospitalId"
                @change="handleDepartmentChange"
                class="form-select"
              >
                <option value="">请选择科室</option>
                <option v-for="dept in departments" :key="dept.id" :value="dept.id">
                  {{ dept.id }} - {{ dept.name }}
                </option>
              </select>
            </div>
          </template>
          <div class="form-group form-tip">
            <p class="tip-text">
              用户账号将由系统自动生成（医院编号-科室编号-用户序号），无需手动填写。
            </p>
          </div>
          <div class="form-group">
            <label>真实姓名 *</label>
            <input v-model="userForm.fullName" type="text" required />
          </div>
          <div v-if="!showEditUser" class="form-group">
            <label>密码 *</label>
            <input v-model="userForm.password" type="password" required minlength="6" />
          </div>
          <div class="form-group">
            <label>角色 *</label>
            <select v-model="userForm.role" required>
              <option value="doctor">医生</option>
              <option value="admin">管理员</option>
            </select>
          </div>
          <div v-if="formError" class="error-message">{{ formError }}</div>
          <div class="modal-actions">
            <button type="button" @click="closeModals">取消</button>
            <button type="submit" :disabled="formLoading">
              {{ formLoading ? '处理中...' : '确认' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 查看用户详情对话框 -->
    <div v-if="showViewUser" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>用户详情</h3>
        <div class="user-detail">
          <div class="detail-row">
            <span class="label">用户ID:</span>
            <span class="value">{{ selectedUser?.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">真实姓名:</span>
            <span class="value">{{ selectedUser?.fullName }}</span>
          </div>
          <div class="detail-row">
            <span class="label">用户名:</span>
            <span class="value">{{ selectedUser?.username || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">角色:</span>
            <span class="value role-badge" :class="selectedUser?.role">{{ selectedUser?.role }}</span>
          </div>
          <div class="detail-row">
            <span class="label">所属医院:</span>
            <span class="value">{{ selectedUser?.hospitalName || selectedUser?.hospitalId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">所属科室:</span>
            <span class="value">{{ selectedUser?.deptName || selectedUser?.deptCode || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">用户序号:</span>
            <span class="value">{{ selectedUser?.userSeq }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间:</span>
            <span class="value">{{ formatDate(selectedUser?.createdAt) }}</span>
          </div>
          <div v-if="selectedUser?.updatedAt" class="detail-row">
            <span class="label">更新时间:</span>
            <span class="value">{{ formatDate(selectedUser?.updatedAt) }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" @click="closeModals">关闭</button>
        </div>
      </div>
    </div>

    <!-- 重置密码对话框 -->
    <div v-if="showResetPasswordModal" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>重置密码</h3>
        <p>为用户 <strong>{{ selectedUser?.fullName }}</strong> 重置密码</p>
        <form @submit.prevent="handleResetPassword">
          <div class="form-group">
            <label>新密码 *</label>
            <input v-model="newPassword" type="password" required minlength="6" />
          </div>
          <div class="form-group">
            <label>确认新密码 *</label>
            <input v-model="confirmPassword" type="password" required />
          </div>
          <div v-if="formError" class="error-message">{{ formError }}</div>
          <div class="modal-actions">
            <button type="button" @click="closeModals">取消</button>
            <button type="submit" :disabled="formLoading">
              {{ formLoading ? '处理中...' : '确认重置' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除用户 <strong>{{ selectedUser?.fullName }}</strong> 吗？</p>
        <p class="warning">此操作不可恢复！</p>
        <div class="modal-actions">
          <button type="button" @click="closeModals">取消</button>
          <button type="button" @click="handleDelete" class="btn-danger" :disabled="formLoading">
            {{ formLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../utils/api.js'

// 用户管理相关状态
const users = ref([])
const loading = ref(false)
const error = ref('')
const formLoading = ref(false)
const formError = ref('')

const searchKeyword = ref('')
const roleFilter = ref('')

const pagination = ref({
  page: 0,
  size: 10,
  totalPages: 1,
  total: 0,
})

// 医院和科室数据
const hospitals = ref([])
const departments = ref([])

// 对话框状态
const showAddUser = ref(false)
const showEditUser = ref(false)
const showViewUser = ref(false)
const showResetPasswordModal = ref(false)
const showDeleteConfirm = ref(false)

// 表单数据
const userForm = ref({
  hospitalId: '',
  deptCode: '',
  // 用户序号与账号由后端生成，不在前端填写
  userSeq: '',
  username: '',
  fullName: '',
  password: '',
  role: 'doctor',
})

const selectedUser = ref(null)
const newPassword = ref('')
const confirmPassword = ref('')

// 加载医院列表
const fetchHospitals = async () => {
  try {
    const result = await authApi.getAllHospitals()
    hospitals.value = Array.isArray(result) ? result : []
  } catch (err) {
    console.error('加载医院列表失败:', err)
  }
}

// 加载科室列表
const fetchDepartments = async (hospitalId) => {
  if (!hospitalId) {
    departments.value = []
    return
  }

  try {
    const result = await authApi.getHospitalDepartments(hospitalId)
    departments.value = Array.isArray(result) ? result : []
  } catch (err) {
    console.error('加载科室列表失败:', err)
  }
}

// 医院选择变化处理
const handleHospitalChange = () => {
  userForm.value.deptCode = ''
  fetchDepartments(userForm.value.hospitalId)
}

// 科室选择变化处理（当前无需额外逻辑，预留扩展）
const handleDepartmentChange = () => {}

// 用户管理相关方法
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

const viewUser = (user) => {
  selectedUser.value = user
  showViewUser.value = true
}

const editUser = (user) => {
  selectedUser.value = user
  userForm.value = {
    hospitalId: user.hospitalId,
    deptCode: user.deptCode,
    userSeq: user.userSeq,
    username: user.username || '',
    fullName: user.fullName,
    password: '',
    role: user.role,
  }
  showEditUser.value = true
  // 编辑时，加载对应医院的科室列表
  if (user.hospitalId) {
    fetchDepartments(user.hospitalId)
  }
}

const showResetPassword = (user) => {
  selectedUser.value = user
  newPassword.value = ''
  confirmPassword.value = ''
  showResetPasswordModal.value = true
}

const confirmDelete = (user) => {
  selectedUser.value = user
  showDeleteConfirm.value = true
}

const handleUserSubmit = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    if (showEditUser.value) {
      // 编辑用户时，只修改允许修改的字段：姓名和角色
      // 账号(username)、所属医院(hospitalId)、科室(deptCode)不允许修改
      await authApi.updateUser(selectedUser.value.id, {
        fullName: userForm.value.fullName,
        role: userForm.value.role,
      })
    } else {
      // 新增用户时，账号和用户序号由后端生成
      const payload = {
        hospitalId: userForm.value.hospitalId,
        deptCode: userForm.value.deptCode,
        fullName: userForm.value.fullName,
        password: userForm.value.password,
        role: userForm.value.role,
      }
      await authApi.register(payload)
    }
    await fetchUsers()
    closeModals()
  } catch (err) {
    formError.value = err.message || (showEditUser.value ? '更新用户失败' : '添加用户失败')
  } finally {
    formLoading.value = false
  }
}

const handleResetPassword = async () => {
  formError.value = ''

  if (newPassword.value !== confirmPassword.value) {
    formError.value = '两次输入的密码不一致'
    return
  }

  formLoading.value = true

  try {
    await authApi.resetPassword(selectedUser.value.id, newPassword.value)
    alert('密码重置成功！')
    closeModals()
  } catch (err) {
    formError.value = err.message || '重置密码失败'
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async () => {
  formLoading.value = true

  try {
    await authApi.deleteUser(selectedUser.value.id)
    await fetchUsers()
    closeModals()
  } catch (err) {
    formError.value = err.message || '删除用户失败'
  } finally {
    formLoading.value = false
  }
}

// 关闭所有对话框
const closeModals = () => {
  showAddUser.value = false
  showEditUser.value = false
  showViewUser.value = false
  showResetPasswordModal.value = false
  showDeleteConfirm.value = false
  userForm.value = {
    hospitalId: '',
    deptCode: '',
    userSeq: '',
    username: '',
    fullName: '',
    password: '',
    role: 'doctor',
  }
  selectedUser.value = null
  newPassword.value = ''
  confirmPassword.value = ''
  formError.value = ''
  // 清除科室列表
  departments.value = []
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUsers()
  fetchHospitals()
})
</script>

<style scoped>
.user-manage-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 80vh;
}

/* 导航栏样式 */
.nav-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1.5rem 2rem;
}

.nav-bar h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

/* 标签页内容样式 */
.tab-content {
  padding: 0;
}

.toolbar {
  padding: 1.5rem;
  border-bottom: 1px solid #e0e0e0;
  background: #f9f9f9;
}

.toolbar h2 {
  margin: 0 0 1rem 0;
  color: #333;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  align-items: center;
}

.actions input,
.actions select {
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
}

.actions input {
  width: 220px;
}

.actions select {
  min-width: 150px;
}

.actions input:focus,
.actions select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.actions button {
  padding: 0.6rem 1.2rem;
  background: #f0f0f0;
  color: #333;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
}

.actions button:hover {
  background: #e0e0e0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-primary {
  background: #667eea !important;
  color: white !important;
}

.btn-primary:hover {
  background: #5568d3 !important;
}

.loading, .error {
  text-align: center;
  padding: 4rem;
  color: #666;
  font-size: 1.1rem;
}

.error {
  color: #e74c3c;
}

.content {
  padding: 0;
}

.table-container {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

.user-table th,
.user-table td {
  padding: 1rem 1.25rem;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.user-table th {
  background: #f9f9f9;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.user-table tr {
  transition: background-color 0.3s;
}

.user-table tr:hover {
  background: #f5f5f5;
}

.role-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
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

.user-table .actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn-small {
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  font-weight: 500;
}

.btn-warning {
  background: #ff9800 !important;
  color: white !important;
}

.btn-danger {
  background: #e74c3c !important;
  color: white !important;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  border-top: 1px solid #e0e0e0;
  background: #f9f9f9;
}

.pagination button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s;
}

.pagination button:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #667eea;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination span {
  color: #666;
  font-size: 0.9rem;
}

/* 模态框样式 */
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
  backdrop-filter: blur(2px);
}

.modal {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  animation: modalFadeIn 0.3s ease-out;
}

@keyframes modalFadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal h3 {
  margin: 0 0 1.5rem 0;
  color: #333;
  font-size: 1.2rem;
  font-weight: 600;
}

.form-group {
  margin-bottom: 1.2rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
  font-size: 0.95rem;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.form-group input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.error-message {
  color: #e74c3c;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.modal-actions {
  display: flex;
  gap: 1rem;
  margin-top: 2rem;
}

.modal-actions button {
  flex: 1;
  padding: 0.85rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
}

.modal-actions button[type="button"] {
  background: #f0f0f0;
  color: #666;
}

.modal-actions button[type="button"]:hover {
  background: #e0e0e0;
  transform: translateY(-1px);
}

.modal-actions button[type="submit"] {
  background: #667eea;
  color: white;
}

.modal-actions button[type="submit"]:hover:not(:disabled) {
  background: #5568d3;
  transform: translateY(-1px);
}

.modal-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.user-detail {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.3s;
}

.detail-row:hover {
  background: #f9f9f9;
}

.detail-row .label {
  color: #666;
  font-weight: 500;
  font-size: 0.95rem;
}

.detail-row .value {
  color: #333;
  text-align: right;
  font-size: 0.95rem;
  word-break: break-all;
}

.warning {
  color: #e74c3c;
  font-weight: 500;
  margin-top: 0.5rem;
  font-size: 0.9rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .actions input,
  .actions select,
  .actions button {
    width: 100%;
  }

  .user-table th,
  .user-table td {
    padding: 0.75rem;
    font-size: 0.9rem;
  }

  .user-table .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .user-table .actions button {
    width: 100%;
    margin-bottom: 0.5rem;
  }

  .pagination {
    flex-wrap: wrap;
  }

  .modal {
    margin: 1rem;
    padding: 1.5rem;
  }
}
</style>
