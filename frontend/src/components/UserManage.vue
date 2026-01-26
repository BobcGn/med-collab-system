<template>
  <div class="user-manage-container">
    <!-- 导航栏 -->
    <div class="nav-bar">
      <h2>管理员控制台</h2>
      <div class="nav-tabs">
        <button 
          class="nav-tab" 
          :class="{ active: activeTab === 'users' }"
          @click="switchTab('users')"
        >
          用户管理
        </button>
        <button 
          class="nav-tab" 
          :class="{ active: activeTab === 'hospitals' }"
          @click="switchTab('hospitals')"
        >
          医院管理
        </button>
        <button 
          class="nav-tab" 
          :class="{ active: activeTab === 'departments' }"
          @click="switchTab('departments')"
        >
          科室管理
        </button>
      </div>
    </div>

    <!-- 用户管理 -->
    <div v-if="activeTab === 'users'" class="tab-content">
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
            <option value="nurse">护士</option>
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
                <th>医院ID</th>
                <th>科室</th>
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
                <td>{{ user.hospitalId }}</td>
                <td>{{ user.deptCode }}</td>
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

    <!-- 医院管理 -->
    <div v-if="activeTab === 'hospitals'" class="tab-content">
      <div class="toolbar">
        <div class="actions">
          <input
            v-model="hospitalSearch"
            type="text"
            placeholder="搜索医院..."
            @input="handleHospitalSearch"
          />
          <button @click="showAddHospital = true" class="btn-primary">添加医院</button>
          <button @click="fetchHospitals">刷新</button>
        </div>
      </div>

      <div v-if="hospitalsLoading" class="loading">加载中...</div>
      <div v-else-if="hospitalsError" class="error">{{ hospitalsError }}</div>
      <div v-else class="content">
        <div class="table-container">
          <table class="user-table">
            <thead>
              <tr>
                <th>医院ID</th>
                <th>医院名称</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="hospital in hospitals" :key="hospital.id">
                <td>{{ hospital.id }}</td>
                <td>{{ hospital.name }}</td>
                <td>{{ formatDate(hospital.createdAt) }}</td>
                <td class="actions">
                  <button @click="viewHospital(hospital)" class="btn-small">查看</button>
                  <button @click="editHospital(hospital)" class="btn-small btn-primary">编辑</button>
                  <button @click="confirmDeleteHospital(hospital)" class="btn-small btn-danger">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 科室管理 -->
    <div v-if="activeTab === 'departments'" class="tab-content">
      <div class="toolbar">
        <div class="actions">
          <select v-model="departmentHospitalId" @change="fetchDepartments">
            <option value="">选择医院</option>
            <option v-for="hospital in hospitals" :key="hospital.id" :value="hospital.id">
              {{ hospital.name }}
            </option>
          </select>
          <input
            v-model="departmentSearch"
            type="text"
            placeholder="搜索科室..."
            @input="handleDepartmentSearch"
          />
          <button @click="showAddDepartment = true" class="btn-primary">添加科室</button>
          <button @click="fetchDepartments">刷新</button>
        </div>
      </div>

      <div v-if="departmentsLoading" class="loading">加载中...</div>
      <div v-else-if="departmentsError" class="error">{{ departmentsError }}</div>
      <div v-else class="content">
        <div class="table-container">
          <table class="user-table">
            <thead>
              <tr>
                <th>科室ID</th>
                <th>科室名称</th>
                <th>所属医院</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dept in departments" :key="dept.id">
                <td>{{ dept.id }}</td>
                <td>{{ dept.name }}</td>
                <td>{{ getHospitalName(dept.hospitalId) }}</td>
                <td>{{ formatDate(dept.createdAt) }}</td>
                <td class="actions">
                  <button @click="viewDepartment(dept)" class="btn-small">查看</button>
                  <button @click="editDepartment(dept)" class="btn-small btn-primary">编辑</button>
                  <button @click="confirmDeleteDepartment(dept)" class="btn-small btn-danger">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 添加/编辑用户对话框 -->
    <div v-if="showAddUser || showEditUser" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditUser ? '编辑用户' : '添加用户' }}</h3>
        <form @submit.prevent="handleUserSubmit">
          <div class="form-group">
            <label>医院ID *</label>
            <input v-model="userForm.hospitalId" type="text" required :disabled="showEditUser" />
          </div>
          <div class="form-group">
            <label>科室代码 *</label>
            <input v-model="userForm.deptCode" type="text" required :disabled="showEditUser" />
          </div>
          <div class="form-group">
            <label>用户序号 *</label>
            <input v-model="userForm.userSeq" type="text" required :disabled="showEditUser" />
          </div>
          <div class="form-group">
            <label>用户名</label>
            <input v-model="userForm.username" type="text" />
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
              <option value="nurse">护士</option>
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
            <span class="label">医院ID:</span>
            <span class="value">{{ selectedUser?.hospitalId }}</span>
          </div>
          <div class="detail-row">
            <span class="label">科室代码:</span>
            <span class="value">{{ selectedUser?.deptCode }}</span>
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

    <!-- 医院管理对话框 -->
    <div v-if="showAddHospital || showEditHospital" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditHospital ? '编辑医院' : '添加医院' }}</h3>
        <form @submit.prevent="handleHospitalSubmit">
          <div class="form-group">
            <label>医院ID *</label>
            <input v-model="hospitalForm.id" type="text" required :disabled="showEditHospital" />
          </div>
          <div class="form-group">
            <label>医院名称 *</label>
            <input v-model="hospitalForm.name" type="text" required />
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

    <div v-if="showViewHospitalModal" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>医院详情</h3>
        <div class="user-detail">
          <div class="detail-row">
            <span class="label">医院ID:</span>
            <span class="value">{{ selectedHospital?.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">医院名称:</span>
            <span class="value">{{ selectedHospital?.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间:</span>
            <span class="value">{{ formatDate(selectedHospital?.createdAt) }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" @click="closeModals">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="showDeleteHospitalConfirm" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除医院 <strong>{{ selectedHospital?.name }}</strong> 吗？</p>
        <p class="warning">此操作不可恢复！</p>
        <div class="modal-actions">
          <button type="button" @click="closeModals">取消</button>
          <button type="button" @click="handleDeleteHospital" class="btn-danger" :disabled="formLoading">
            {{ formLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 科室管理对话框 -->
    <div v-if="showAddDepartment || showEditDepartment" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditDepartment ? '编辑科室' : '添加科室' }}</h3>
        <form @submit.prevent="handleDepartmentSubmit">
          <div class="form-group">
            <label>科室ID *</label>
            <input v-model="departmentForm.id" type="text" required :disabled="showEditDepartment" />
          </div>
          <div class="form-group">
            <label>科室名称 *</label>
            <input v-model="departmentForm.name" type="text" required />
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

    <div v-if="showViewDepartmentModal" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>科室详情</h3>
        <div class="user-detail">
          <div class="detail-row">
            <span class="label">科室ID:</span>
            <span class="value">{{ selectedDepartment?.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">科室名称:</span>
            <span class="value">{{ selectedDepartment?.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">所属医院:</span>
            <span class="value">{{ getHospitalName(selectedDepartment?.hospitalId) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间:</span>
            <span class="value">{{ formatDate(selectedDepartment?.createdAt) }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" @click="closeModals">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="showDeleteDepartmentConfirm" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除科室 <strong>{{ selectedDepartment?.name }}</strong> 吗？</p>
        <p class="warning">此操作不可恢复！</p>
        <div class="modal-actions">
          <button type="button" @click="closeModals">取消</button>
          <button type="button" @click="handleDeleteDepartment" class="btn-danger" :disabled="formLoading">
            {{ formLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../utils/api'

// 标签页状态
const activeTab = ref('users')

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

// 医院管理相关状态
const hospitals = ref([])
const hospitalsLoading = ref(false)
const hospitalsError = ref('')
const hospitalSearch = ref('')

// 科室管理相关状态
const departments = ref([])
const departmentsLoading = ref(false)
const departmentsError = ref('')
const departmentSearch = ref('')
const departmentHospitalId = ref('')

// 对话框状态
const showAddUser = ref(false)
const showEditUser = ref(false)
const showViewUser = ref(false)
const showResetPasswordModal = ref(false)
const showDeleteConfirm = ref(false)

const showAddHospital = ref(false)
const showEditHospital = ref(false)
const showViewHospitalModal = ref(false)
const showDeleteHospitalConfirm = ref(false)

const showAddDepartment = ref(false)
const showEditDepartment = ref(false)
const showViewDepartmentModal = ref(false)
const showDeleteDepartmentConfirm = ref(false)

// 表单数据
const userForm = ref({
  hospitalId: '',
  deptCode: '',
  userSeq: '',
  username: '',
  fullName: '',
  password: '',
  role: 'doctor',
})

const hospitalForm = ref({
  id: '',
  name: '',
})

const departmentForm = ref({
  id: '',
  hospitalId: '',
  name: '',
})

const selectedUser = ref(null)
const selectedHospital = ref(null)
const selectedDepartment = ref(null)
const newPassword = ref('')
const confirmPassword = ref('')

// 切换标签页
const switchTab = (tab) => {
  activeTab.value = tab
  if (tab === 'hospitals') {
    fetchHospitals()
  } else if (tab === 'departments') {
    fetchHospitals() // 先获取医院列表
  }
}

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
      await authApi.updateUser(selectedUser.value.id, {
        username: userForm.value.username || null,
        fullName: userForm.value.fullName,
        role: userForm.value.role,
      })
    } else {
      await authApi.register(userForm.value)
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

// 医院管理相关方法
const fetchHospitals = async () => {
  hospitalsLoading.value = true
  hospitalsError.value = ''

  try {
    // 这里需要调用获取医院列表的API
    // 暂时使用模拟数据
    hospitals.value = [
      { id: 'H001', name: '北京协和医院', createdAt: new Date().toISOString() },
      { id: 'H002', name: '上海瑞金医院', createdAt: new Date().toISOString() },
      { id: 'H003', name: '广州中山大学附属医院', createdAt: new Date().toISOString() },
    ]
  } catch (err) {
    hospitalsError.value = err.message || '获取医院列表失败'
  } finally {
    hospitalsLoading.value = false
  }
}

const handleHospitalSearch = async () => {
  if (!hospitalSearch.value.trim()) {
    fetchHospitals()
    return
  }

  hospitalsLoading.value = true
  hospitalsError.value = ''

  try {
    // 这里需要调用搜索医院的API
    // 暂时使用模拟数据
    hospitals.value = hospitals.value.filter(hospital => 
      hospital.name.includes(hospitalSearch.value) || 
      hospital.id.includes(hospitalSearch.value)
    )
  } catch (err) {
    hospitalsError.value = err.message || '搜索失败'
  } finally {
    hospitalsLoading.value = false
  }
}

const viewHospital = (hospital) => {
  selectedHospital.value = hospital
  showViewHospitalModal.value = true
}

const editHospital = (hospital) => {
  selectedHospital.value = hospital
  hospitalForm.value = {
    id: hospital.id,
    name: hospital.name,
  }
  showEditHospital.value = true
}

const confirmDeleteHospital = (hospital) => {
  selectedHospital.value = hospital
  showDeleteHospitalConfirm.value = true
}

const handleHospitalSubmit = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    // 这里需要调用添加或更新医院的API
    // 暂时使用模拟数据
    if (showEditHospital.value) {
      const index = hospitals.value.findIndex(h => h.id === selectedHospital.value.id)
      if (index !== -1) {
        hospitals.value[index] = { ...selectedHospital.value, name: hospitalForm.value.name }
      }
    } else {
      hospitals.value.push({
        id: hospitalForm.value.id,
        name: hospitalForm.value.name,
        createdAt: new Date().toISOString(),
      })
    }
    closeModals()
  } catch (err) {
    formError.value = err.message || (showEditHospital.value ? '更新医院失败' : '添加医院失败')
  } finally {
    formLoading.value = false
  }
}

const handleDeleteHospital = async () => {
  formLoading.value = true

  try {
    // 这里需要调用删除医院的API
    // 暂时使用模拟数据
    hospitals.value = hospitals.value.filter(h => h.id !== selectedHospital.value.id)
    closeModals()
  } catch (err) {
    formError.value = err.message || '删除医院失败'
  } finally {
    formLoading.value = false
  }
}

// 科室管理相关方法
const fetchDepartments = async () => {
  if (!departmentHospitalId.value) {
    departments.value = []
    return
  }

  departmentsLoading.value = true
  departmentsError.value = ''

  try {
    // 这里需要调用获取科室列表的API
    // 暂时使用模拟数据
    const deptData = {
      'H001': [
        { id: 'D001', hospitalId: 'H001', name: '内科', createdAt: new Date().toISOString() },
        { id: 'D002', hospitalId: 'H001', name: '外科', createdAt: new Date().toISOString() },
        { id: 'D003', hospitalId: 'H001', name: '儿科', createdAt: new Date().toISOString() },
      ],
      'H002': [
        { id: 'D001', hospitalId: 'H002', name: '内科', createdAt: new Date().toISOString() },
        { id: 'D002', hospitalId: 'H002', name: '外科', createdAt: new Date().toISOString() },
        { id: 'D004', hospitalId: 'H002', name: '妇产科', createdAt: new Date().toISOString() },
      ],
      'H003': [
        { id: 'D001', hospitalId: 'H003', name: '内科', createdAt: new Date().toISOString() },
        { id: 'D002', hospitalId: 'H003', name: '外科', createdAt: new Date().toISOString() },
        { id: 'D005', hospitalId: 'H003', name: '神经内科', createdAt: new Date().toISOString() },
      ],
    }
    departments.value = deptData[departmentHospitalId.value] || []
  } catch (err) {
    departmentsError.value = err.message || '获取科室列表失败'
  } finally {
    departmentsLoading.value = false
  }
}

const handleDepartmentSearch = async () => {
  if (!departmentSearch.value.trim()) {
    fetchDepartments()
    return
  }

  departmentsLoading.value = true
  departmentsError.value = ''

  try {
    // 这里需要调用搜索科室的API
    // 暂时使用模拟数据
    const originalDepartments = departments.value
    departments.value = originalDepartments.filter(dept => 
      dept.name.includes(departmentSearch.value) || 
      dept.id.includes(departmentSearch.value)
    )
  } catch (err) {
    departmentsError.value = err.message || '搜索失败'
  } finally {
    departmentsLoading.value = false
  }
}

const viewDepartment = (dept) => {
  selectedDepartment.value = dept
  showViewDepartmentModal.value = true
}

const editDepartment = (dept) => {
  selectedDepartment.value = dept
  departmentForm.value = {
    id: dept.id,
    hospitalId: dept.hospitalId,
    name: dept.name,
  }
  showEditDepartment.value = true
}

const confirmDeleteDepartment = (dept) => {
  selectedDepartment.value = dept
  showDeleteDepartmentConfirm.value = true
}

const handleDepartmentSubmit = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    // 这里需要调用添加或更新科室的API
    // 暂时使用模拟数据
    if (showEditDepartment.value) {
      const index = departments.value.findIndex(d => d.id === selectedDepartment.value.id)
      if (index !== -1) {
        departments.value[index] = { ...selectedDepartment.value, name: departmentForm.value.name }
      }
    } else {
      departments.value.push({
        id: departmentForm.value.id,
        hospitalId: departmentHospitalId.value,
        name: departmentForm.value.name,
        createdAt: new Date().toISOString(),
      })
    }
    closeModals()
  } catch (err) {
    formError.value = err.message || (showEditDepartment.value ? '更新科室失败' : '添加科室失败')
  } finally {
    formLoading.value = false
  }
}

const handleDeleteDepartment = async () => {
  formLoading.value = true

  try {
    // 这里需要调用删除科室的API
    // 暂时使用模拟数据
    departments.value = departments.value.filter(d => d.id !== selectedDepartment.value.id)
    closeModals()
  } catch (err) {
    formError.value = err.message || '删除科室失败'
  } finally {
    formLoading.value = false
  }
}

// 获取医院名称
const getHospitalName = (hospitalId) => {
  const hospital = hospitals.value.find(h => h.id === hospitalId)
  return hospital ? hospital.name : hospitalId
}

// 关闭所有对话框
const closeModals = () => {
  // 关闭用户相关对话框
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

  // 关闭医院相关对话框
  showAddHospital.value = false
  showEditHospital.value = false
  showViewHospitalModal.value = false
  showDeleteHospitalConfirm.value = false
  hospitalForm.value = {
    id: '',
    name: '',
  }
  selectedHospital.value = null

  // 关闭科室相关对话框
  showAddDepartment.value = false
  showEditDepartment.value = false
  showViewDepartmentModal.value = false
  showDeleteDepartmentConfirm.value = false
  departmentForm.value = {
    id: '',
    hospitalId: '',
    name: '',
  }
  selectedDepartment.value = null

  formError.value = ''
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchUsers()
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
  margin: 0 0 1.5rem 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.nav-tabs {
  display: flex;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.1);
  padding: 0.5rem;
  border-radius: 8px;
  max-width: fit-content;
}

.nav-tab {
  padding: 0.75rem 1.5rem;
  background: transparent;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
  white-space: nowrap;
}

.nav-tab:hover {
  background: rgba(255, 255, 255, 0.2);
}

.nav-tab.active {
  background: white;
  color: #667eea;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
  .nav-tabs {
    flex-wrap: wrap;
    max-width: 100%;
  }

  .nav-tab {
    padding: 0.6rem 1.2rem;
    font-size: 0.9rem;
  }

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
