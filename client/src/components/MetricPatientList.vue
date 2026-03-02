<template>
  <div class="metric-patient-list">
    <div class="nav-bar">
      <h2>{{ title }}</h2>
    </div>

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
              <td>{{ patient.attendingDoctorName || patient.attendingDoctorId || patient.doctorId || '-' }}</td>
              <td>
                <span class="status-badge" :class="patient.status">{{ formatStatus(patient.status) }}</span>
              </td>
              <td>{{ formatDate(patient.lastVisitDate) }}</td>
              <td class="actions">
                <div class="action-buttons">
                  <button @click="handlePatientClick(patient)" class="btn-small btn-view">查看</button>
                </div>
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
</template>

<script setup>
import { ref, onMounted, defineProps, defineEmits } from 'vue'
import { patientApi } from '../utils/api.js'

const props = defineProps({
  title: {
    type: String,
    default: '患者列表'
  },
  source: {
    type: String,
    required: true // 'image', 'analysis', 'report'
  }
})

const emit = defineEmits(['patientClick'])

// 患者管理相关状态
const patients = ref([])
const loading = ref(false)
const error = ref('')

const searchKeyword = ref('')
const statusFilter = ref('')

const pagination = ref({
  page: 0,
  size: 10,
  totalPages: 1,
  total: 0,
})

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

const handlePatientClick = (patient) => {
  emit('patientClick', patient)
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
.metric-patient-list {
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

.toolbar {
  padding: 1.5rem;
  border-bottom: 1px solid #e0e0e0;
  background: #f9f9f9;
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
  text-decoration: none;
  display: inline-block;
  text-align: center;
}

.actions button:hover {
  background: #e0e0e0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
  justify-content: center;
  align-items: center;
  padding: 0.5rem 0;
}

.action-buttons {
  display: flex;
  gap: 0.3rem;
  flex-wrap: nowrap;
}

.btn-small {
  padding: 0.35rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 500;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 50px;
}

.btn-small:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-view {
  background: #e3f2fd;
  color: #1976d2;
  border-color: #bbdefb;
}

.btn-view:hover {
  background: #bbdefb;
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
}
</style>