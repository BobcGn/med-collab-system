<template>
  <div class="hospital-manage-container">
    <!-- 导航栏 -->
    <div class="nav-bar">
      <h2>医院与科室管理</h2>
      <div class="nav-tabs">
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
          <table class="manage-table">
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
          <table class="manage-table">
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

    <!-- 医院管理对话框 -->
    <div v-if="showAddHospital || showEditHospital" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditHospital ? '编辑医院' : '添加医院' }}</h3>
        <form @submit.prevent="handleHospitalSubmit">
          <div class="form-group">
            <label>医院ID *</label>
            <input v-model="hospitalForm.id" type="text" required :disabled="showEditHospital" placeholder="例如：BJH、SHH、GZH" />
          </div>
          <div class="form-group">
            <label>医院名称 *</label>
            <input v-model="hospitalForm.name" type="text" required placeholder="请输入医院全称" />
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
        <div class="detail-info">
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
            <label>所属医院 *</label>
            <select v-model="departmentForm.hospitalId" required :disabled="showEditDepartment">
              <option value="">请选择医院</option>
              <option v-for="hospital in hospitals" :key="hospital.id" :value="hospital.id">
                {{ hospital.name }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>科室ID *</label>
            <input v-model="departmentForm.id" type="text" required :disabled="showEditDepartment" placeholder="例如：D001、D002" />
          </div>
          <div class="form-group">
            <label>科室名称 *</label>
            <input v-model="departmentForm.name" type="text" required placeholder="请输入科室名称" />
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
        <div class="detail-info">
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
import { authApi } from '../utils/api.js'

// 标签页状态
const activeTab = ref('hospitals')

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
const showAddHospital = ref(false)
const showEditHospital = ref(false)
const showViewHospitalModal = ref(false)
const showDeleteHospitalConfirm = ref(false)

const showAddDepartment = ref(false)
const showEditDepartment = ref(false)
const showViewDepartmentModal = ref(false)
const showDeleteDepartmentConfirm = ref(false)

// 表单相关状态
const formLoading = ref(false)
const formError = ref('')

const hospitalForm = ref({
  id: '',
  name: '',
})

const departmentForm = ref({
  id: '',
  hospitalId: '',
  name: '',
})

const selectedHospital = ref(null)
const selectedDepartment = ref(null)

// 切换标签页
const switchTab = (tab) => {
  activeTab.value = tab
  if (tab === 'hospitals') {
    fetchHospitals()
  } else if (tab === 'departments') {
    fetchHospitals() // 先获取医院列表
  }
}

// 医院管理相关方法
const fetchHospitals = async () => {
  hospitalsLoading.value = true
  hospitalsError.value = ''

  try {
    const result = await authApi.getAllHospitals()
    hospitals.value = Array.isArray(result) ? result : []
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
    const allHospitals = await authApi.getAllHospitals()
    hospitals.value = (Array.isArray(allHospitals) ? allHospitals : []).filter(hospital =>
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
    if (showEditHospital.value) {
      await authApi.updateHospital(selectedHospital.value.id, {
        name: hospitalForm.value.name,
      })
    } else {
      await authApi.createHospital(hospitalForm.value)
    }
    await fetchHospitals()
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
    await authApi.deleteHospital(selectedHospital.value.id)
    await fetchHospitals()
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
    const result = await authApi.getHospitalDepartments(departmentHospitalId.value)
    departments.value = Array.isArray(result) ? result : []
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
    const allDepartments = await authApi.getHospitalDepartments(departmentHospitalId.value)
    departments.value = (Array.isArray(allDepartments) ? allDepartments : []).filter(dept =>
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
    if (showEditDepartment.value) {
      await authApi.updateDepartment(selectedDepartment.value.id, {
        name: departmentForm.value.name,
      })
    } else {
      await authApi.createDepartment(departmentForm.value)
    }
    await fetchDepartments()
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
    await authApi.deleteDepartment(selectedDepartment.value.id)
    await fetchDepartments()
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
  fetchHospitals()
})
</script>

<style scoped>
.hospital-manage-container {
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

.manage-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

.manage-table th,
.manage-table td {
  padding: 1rem 1.25rem;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.manage-table th {
  background: #f9f9f9;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.manage-table tr {
  transition: background-color 0.3s;
}

.manage-table tr:hover {
  background: #f5f5f5;
}

.manage-table .actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn-small {
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  font-weight: 500;
}

.btn-danger {
  background: #e74c3c !important;
  color: white !important;
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

.form-group input:disabled,
.form-group select:disabled {
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

.detail-info {
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

  .manage-table th,
  .manage-table td {
    padding: 0.75rem;
    font-size: 0.9rem;
  }

  .manage-table .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .manage-table .actions button {
    width: 100%;
    margin-bottom: 0.5rem;
  }

  .modal {
    margin: 1rem;
    padding: 1.5rem;
  }
}
</style>
