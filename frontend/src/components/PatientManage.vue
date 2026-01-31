<template>
  <div class="patient-manage-container">
    <div class="nav-bar">
      <h2>患者管理</h2>
    </div>

    <div class="tab-content">
      <div class="toolbar">
        <div class="actions">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索患者..."
            @input="handleSearch"
          />
          <select v-model="statusFilter" @change="fetchPatients">
            <option value="">全部状态</option>
            <option value="Active">激活</option>
            <option value="Discharged">出院</option>
            <option value="Deceased">死亡</option>
          </select>
          <button @click="showAddPatient = true" class="btn-primary">新增患者</button>
          <button @click="fetchPatients">刷新</button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="content">
        <div class="table-container">
          <table class="patient-table">
            <thead>
              <tr>
                <th>患者姓名</th>
                <th>性别</th>
                <th>年龄</th>
                <th>血型</th>
                <th>主治医生</th>
                <th>状态</th>
                <th>最近就诊</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="patients.length > 0" v-for="patient in patients" :key="patient.id">
                <td>{{ patient.name }}</td>
                <td>{{ formatGender(patient.gender) }}</td>
                <td>{{ patient.age || '-' }}</td>
                <td>{{ formatBloodType(patient.bloodType) }}</td>
                <td>{{ patient.attendingDoctorId || patient.doctorId || '-' }}</td>
                <td>
                  <span class="status-badge" :class="patient.status">{{ formatStatus(patient.status) }}</span>
                </td>
                <td>{{ formatDate(patient.lastVisitDate) }}</td>
                <td class="actions">
                  <button @click="viewPatient(patient)" class="btn-small">查看</button>
                  <button @click="editPatient(patient)" class="btn-small btn-primary">编辑</button>
                  <button @click="showChangeStatus(patient)" class="btn-small btn-warning">状态</button>
                  <button @click="confirmDelete(patient)" class="btn-small btn-danger">删除</button>
                </td>
              </tr>
              <tr v-if="patients.length === 0">
                <td colspan="8" class="empty-state">
                  <div class="empty-content">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                      <circle cx="9" cy="7" r="4"></circle>
                      <line x1="23" y1="11" x2="17" y2="11"></line>
                      <line x1="23" y1="7" x2="17" y2="7"></line>
                    </svg>
                    <p>暂无患者数据</p>
                    <p v-if="!searchKeyword && !statusFilter">点击上方"新增患者"按钮添加</p>
                  </div>
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

    <!-- 添加/编辑患者对话框 -->
    <div v-if="showAddPatient || showEditPatient" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>{{ showEditPatient ? '编辑患者' : '新增患者' }}</h3>
        <form @submit.prevent="handlePatientSubmit">
          <div class="form-group">
            <label>患者姓名 *</label>
            <input v-model="patientForm.name" type="text" required />
          </div>
          <div class="form-group">
            <label>性别 *</label>
            <select v-model="patientForm.gender" required>
              <option value="M">男</option>
              <option value="F">女</option>
            </select>
          </div>
          <div class="form-group">
            <label>出生日期</label>
            <input v-model="patientForm.birthDate" type="date" />
          </div>
          <div class="form-group">
            <label>血型</label>
            <select v-model="patientForm.bloodType">
              <option value="">未知</option>
              <option value="A">A型</option>
              <option value="B">B型</option>
              <option value="O">O型</option>
              <option value="AB">AB型</option>
            </select>
          </div>
          <div class="form-group">
            <label>联系电话 *</label>
            <input v-model="patientForm.phone" type="tel" required />
          </div>
          <div class="form-group">
            <label>主治医生ID</label>
            <input v-model="patientForm.doctorId" type="text" />
          </div>
          <div class="form-group">
            <label>主治医院ID</label>
            <input v-model="patientForm.hospitalId" type="text" />
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

    <!-- 查看患者详情对话框 -->
    <div v-if="showViewPatient" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>患者详情</h3>
        <div class="patient-detail">
          <div class="detail-row">
            <span class="label">患者ID:</span>
            <span class="value">{{ selectedPatient?.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">患者姓名:</span>
            <span class="value">{{ selectedPatient?.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">性别:</span>
            <span class="value">{{ formatGender(selectedPatient?.gender) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">年龄:</span>
            <span class="value">{{ selectedPatient?.age || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">血型:</span>
            <span class="value">{{ formatBloodType(selectedPatient?.bloodType) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">联系电话:</span>
            <span class="value">{{ selectedPatient?.phone }}</span>
          </div>
          <div class="detail-row">
            <span class="label">主治医生:</span>
            <span class="value">{{ selectedPatient?.doctorId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">所属医院:</span>
            <span class="value">{{ selectedPatient?.hospitalId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">状态:</span>
            <span class="value status-badge" :class="selectedPatient?.status">
              {{ formatStatus(selectedPatient?.status) }}
            </span>
          </div>
          <div class="detail-row">
            <span class="label">入院时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.admissionDate) }}</span>
          </div>
          <div v-if="selectedPatient?.dischargeDate" class="detail-row">
            <span class="label">出院时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.dischargeDate) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.createdAt) }}</span>
          </div>
          <div v-if="selectedPatient?.updatedAt" class="detail-row">
            <span class="label">更新时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.updatedAt) }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" @click="closeModals">关闭</button>
        </div>
      </div>
    </div>

    <!-- 更改状态对话框 -->
    <div v-if="showChangeStatusModal" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>更改患者状态</h3>
        <p>为患者 <strong>{{ selectedPatient?.name }}</strong> 更改状态</p>
        <form @submit.prevent="handleStatusChange">
          <div class="form-group">
            <label>新状态 *</label>
            <select v-model="newStatus" required>
              <option value="Active">激活</option>
              <option value="Discharged">出院</option>
              <option value="Deceased">死亡</option>
            </select>
          </div>
          <div v-if="formError" class="error-message">{{ formError }}</div>
          <div class="modal-actions">
            <button type="button" @click="closeModals">取消</button>
            <button type="submit" :disabled="formLoading">
              {{ formLoading ? '处理中...' : '确认更改' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除患者 <strong>{{ selectedPatient?.name }}</strong> 吗？</p>
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
import { patientApi } from '../utils/api'

// 患者管理相关状态
const patients = ref([])
const loading = ref(false)
const error = ref('')
const formLoading = ref(false)
const formError = ref('')

const searchKeyword = ref('')
const statusFilter = ref('')

const pagination = ref({
  page: 0,
  size: 10,
  totalPages: 1,
  total: 0,
})

// 对话框状态
const showAddPatient = ref(false)
const showEditPatient = ref(false)
const showViewPatient = ref(false)
const showChangeStatusModal = ref(false)
const showDeleteConfirm = ref(false)

// 表单数据
const patientForm = ref({
  name: '',
  gender: 'M',
  birthDate: '',
  bloodType: '',
  phone: '',
  doctorId: '',
  hospitalId: '',
})

const selectedPatient = ref(null)
const newStatus = ref('Active')

// 患者管理相关方法
const fetchPatients = async () => {
  loading.value = true
  error.value = ''

  try {
    const params = {
      page: pagination.value.page,
      size: pagination.value.size,
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }

    const result = await patientApi.getPatients(params)
    console.log('患者列表响应:', result)

    // 兼容不同的响应格式
    if (result && typeof result === 'object') {
      patients.value = result.patients || result.content || result
      pagination.value.total = result.totalElements || result.total || 0
      pagination.value.totalPages = Math.ceil(pagination.value.total / pagination.value.size) || 1
    } else if (Array.isArray(result)) {
      patients.value = result
      pagination.value.total = result.length
      pagination.value.totalPages = 1
    }
  } catch (err) {
    console.error('获取患者列表失败:', err)
    error.value = `获取患者列表失败: ${err.message || '未知错误'}`
    patients.value = []
    pagination.value.total = 0
    pagination.value.totalPages = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    fetchPatients()
    return
  }

  loading.value = true
  error.value = ''

  try {
    const params = {
      keyword: searchKeyword.value,
      page: 0,
      size: 20,
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const result = await patientApi.searchPatients(params)

    // 兼容不同的响应格式
    if (result && typeof result === 'object') {
      patients.value = result.patients || result.content || []
      pagination.value.total = result.totalElements || result.total || patients.value.length
      pagination.value.totalPages = Math.ceil(pagination.value.total / pagination.value.size) || 1
    } else if (Array.isArray(result)) {
      patients.value = result
      pagination.value.total = result.length
      pagination.value.totalPages = 1
    }
  } catch (err) {
    console.error('搜索失败:', err)
    error.value = err.message || '搜索失败'
    patients.value = []
    pagination.value.total = 0
    pagination.value.totalPages = 0
  } finally {
    loading.value = false
  }
}

const changePage = (newPage) => {
  pagination.value.page = newPage
  fetchPatients()
}

const viewPatient = (patient) => {
  selectedPatient.value = patient
  showViewPatient.value = true
}

const editPatient = (patient) => {
  selectedPatient.value = patient
  patientForm.value = {
    name: patient.name,
    gender: patient.gender,
    birthDate: patient.birthDate || '',
    bloodType: patient.bloodType || '',
    phone: patient.phone,
    doctorId: patient.doctorId || '',
    hospitalId: patient.hospitalId || '',
  }
  showEditPatient.value = true
}

const showChangeStatus = (patient) => {
  selectedPatient.value = patient
  newStatus.value = patient.status
  showChangeStatusModal.value = true
}

const confirmDelete = (patient) => {
  selectedPatient.value = patient
  showDeleteConfirm.value = true
}

const handlePatientSubmit = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    if (showEditPatient.value) {
      await patientApi.updatePatient(selectedPatient.value.id, patientForm.value)
    } else {
      await patientApi.createPatient(patientForm.value)
    }
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || (showEditPatient.value ? '更新患者失败' : '新增患者失败')
  } finally {
    formLoading.value = false
  }
}

const handleStatusChange = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    await patientApi.updateStatus(selectedPatient.value.id, newStatus.value)
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || '更改状态失败'
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async () => {
  formLoading.value = true

  try {
    await patientApi.deletePatient(selectedPatient.value.id)
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || '删除患者失败'
  } finally {
    formLoading.value = false
  }
}

// 关闭所有对话框
const closeModals = () => {
  showAddPatient.value = false
  showEditPatient.value = false
  showViewPatient.value = false
  showChangeStatusModal.value = false
  showDeleteConfirm.value = false
  patientForm.value = {
    name: '',
    gender: 'M',
    birthDate: '',
    bloodType: '',
    phone: '',
    doctorId: '',
    hospitalId: '',
  }
  selectedPatient.value = null
  newStatus.value = 'Active'
  formError.value = ''
}

// 格式化函数
const formatGender = (gender) => {
  const map = { M: '男', F: '女' }
  return map[gender] || gender || '-'
}

const formatBloodType = (bloodType) => {
  const map = { A: 'A型', B: 'B型', O: 'O型', AB: 'AB型', Unknown: '未知' }
  return map[bloodType] || bloodType || '-'
}

const formatStatus = (status) => {
  const map = { Active: '激活', Discharged: '出院', Deceased: '死亡' }
  return map[status] || status || '-'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchPatients()
})
</script>

<style scoped>
.patient-manage-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 80vh;
}

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

.loading,
.error {
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

.patient-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

.patient-table th,
.patient-table td {
  padding: 1rem 1.25rem;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.patient-table th {
  background: #f9f9f9;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.patient-table tr {
  transition: background-color 0.3s;
}

.patient-table tr:hover {
  background: #f5f5f5;
}

.patient-table .empty-state {
  padding: 4rem 2rem;
  text-align: center;
}

.patient-table .empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.patient-table .empty-icon {
  width: 60px;
  height: 60px;
  color: #d9d9d9;
}

.patient-table .empty-content p {
  margin: 0;
  color: #999;
  font-size: 0.95rem;
}

.patient-table .empty-content p:last-child {
  font-size: 0.85rem;
  color: #bbb;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge.Active {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-badge.Discharged {
  background: #fff3e0;
  color: #e65100;
}

.status-badge.Deceased {
  background: #ffebee;
  color: #c62828;
}

.patient-table .actions {
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

.modal p {
  color: #666;
  margin-bottom: 1rem;
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

.patient-detail {
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

  .patient-table th,
  .patient-table td {
    padding: 0.75rem;
    font-size: 0.9rem;
  }

  .patient-table .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .patient-table .actions button {
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
