<template>
  <div class="analysis-result-detail">
    <div class="nav-bar">
      <div class="nav-content">
        <button @click="goBack" class="back-button">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="back-icon">
            <path d="M19 12H5M12 19l-7-7 7-7"></path>
          </svg>
        </button>
        <h2>分析结果详情 - {{ patientName }}</h2>
        <div class="patient-info">
          <span class="patient-id">患者ID: {{ patientId }}</span>
        </div>
      </div>
    </div>

    <div class="content-container">
      <!-- 患者基本信息 -->
      <div class="patient-profile">
        <h3>患者基本信息</h3>
        <div class="profile-grid">
          <div class="profile-item">
            <span class="label">姓名:</span>
            <span class="value">{{ patientName }}</span>
          </div>
          <div class="profile-item">
            <span class="label">性别:</span>
            <span class="value">{{ patientGender }}</span>
          </div>
          <div class="profile-item">
            <span class="label">年龄:</span>
            <span class="value">{{ patientAge === '-' ? '-' : `${patientAge}岁` }}</span>
          </div>
          <div class="profile-item">
            <span class="label">血型:</span>
            <span class="value">{{ patientBloodType }}</span>
          </div>
          <div class="profile-item">
            <span class="label">主治医生:</span>
            <span class="value">{{ patientDoctor }}</span>
          </div>
          <div class="profile-item">
            <span class="label">状态:</span>
            <span class="value status-badge" :class="patientStatus">{{ patientStatusText }}</span>
          </div>
        </div>
      </div>

      <!-- 分析结果列表 -->
      <div class="analysis-list">
        <div class="list-header">
          <h3>分析结果列表</h3>
          <div class="filter-section">
            <select v-model="analysisTypeFilter" @change="filterResults" class="filter-select">
              <option value="">全部类型</option>
              <option value="X-RAY">X射线</option>
              <option value="CT">CT</option>
              <option value="MRI">MRI</option>
              <option value="ULTRASOUND">超声</option>
              <option value="OTHER">其他</option>
            </select>
            <input 
              v-model="searchKeyword" 
              type="text" 
              placeholder="搜索分析结果..." 
              class="search-input"
              @input="filterResults"
            />
          </div>
        </div>

        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="error" class="empty-state">
          <div class="empty-content">
            <p>{{ error }}</p>
          </div>
        </div>
        <div v-else-if="filteredResults.length === 0" class="empty-state">
          <div class="empty-content">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
            <p>暂无分析结果数据</p>
          </div>
        </div>
        <div v-else class="result-cards">
          <div v-for="result in paginatedResults" :key="result.id" class="result-card">
            <div class="card-header">
              <h4>{{ result.analysisType }}</h4>
              <span class="analysis-date">{{ formatDate(result.analysisDate) }}</span>
            </div>
            <div class="card-content">
              <div class="analysis-result">
                <h5>AI分析结果</h5>
                <p>{{ result.result }}</p>
              </div>
              <div class="confidence-score">
                <span class="label">置信度:</span>
                <span class="value">{{ formatConfidence(result.confidence) }}</span>
              </div>
              <div class="doctor-diagnosis" v-if="result.doctorDiagnosis">
                <h5>医生诊断</h5>
                <p>{{ result.doctorDiagnosis }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="!loading && filteredResults.length > 0" class="pagination">
          <button
            :disabled="currentPage === 0"
            @click="changePage(currentPage - 1)"
            class="page-button"
          >
            上一页
          </button>
          <span>第 {{ currentPage + 1 }} / {{ totalPages }} 页</span>
          <span>共 {{ filteredResults.length }} 条</span>
          <button
            :disabled="currentPage >= totalPages - 1"
            @click="changePage(currentPage + 1)"
            class="page-button"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { metricApi, patientApi } from '../utils/api.js'

const router = useRouter()
const route = useRoute()

// 患者信息
const patientId = ref(route.query.patientId || '')
const patientName = ref(route.query.patientName || '未知患者')

const patientGender = ref('-')
const patientAge = ref('-')
const patientBloodType = ref('-')
const patientDoctor = ref('-')
const patientStatus = ref('')
const patientStatusText = ref('-')

// 分析结果相关状态
const analysisResults = ref([])
const loading = ref(false)
const error = ref('')
const analysisTypeFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(0)
const pageSize = ref(5)

// 计算属性
const filteredResults = computed(() => {
  let results = [...analysisResults.value]
  
  // 按类型筛选
  if (analysisTypeFilter.value) {
    results = results.filter(result => result.analysisType === analysisTypeFilter.value)
  }
  
  // 按关键词搜索
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    results = results.filter(result => 
      result.result.toLowerCase().includes(keyword) ||
      result.doctorDiagnosis?.toLowerCase().includes(keyword)
    )
  }
  
  return results
})

const totalPages = computed(() => {
  return Math.ceil(filteredResults.value.length / pageSize.value)
})

// 分页后的数据
const paginatedResults = computed(() => {
  const start = currentPage.value * pageSize.value
  const end = start + pageSize.value
  return filteredResults.value.slice(start, end)
})

const calculateAge = (birthDate) => {
  if (!birthDate) {
    return '-'
  }

  const today = new Date()
  const birth = new Date(birthDate)
  if (Number.isNaN(birth.getTime())) {
    return '-'
  }

  let age = today.getFullYear() - birth.getFullYear()
  const monthDiff = today.getMonth() - birth.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1
  }
  return age >= 0 ? age : '-'
}

const formatPatientStatusText = (status) => {
  const statusMap = {
    Active: '激活',
    Discharged: '出院',
    Deceased: '死亡',
  }
  return statusMap[status] || status || '-'
}

const formatGender = (gender) => {
  const genderMap = {
    M: '男',
    F: '女',
  }
  return genderMap[gender] || gender || '-'
}

const formatMetricLabel = (key) => {
  const labelMap = {
    density: '密度',
    size: '大小',
    location: '位置',
    severity: '严重程度',
    signalIntensity: '信号强度',
    tissueCharacteristics: '组织特征',
    opacity: '不透光度',
    boneStructure: '骨骼结构',
    echogenicity: '回声性',
    bloodFlow: '血流',
    name: '指标',
    value: '值',
    unit: '单位',
    referenceRange: '参考范围',
  }
  return labelMap[key] || key
}

const summarizeMetrics = (metrics) => {
  if (!metrics) {
    return ''
  }

  if (typeof metrics === 'string') {
    return metrics
  }

  const metricItems = Array.isArray(metrics.metrics) ? metrics.metrics : [metrics]
  return metricItems
    .map((metric) => {
      if (!metric || typeof metric !== 'object') {
        return ''
      }

      return Object.entries(metric)
        .filter(([key, value]) => key !== 'confidence' && value != null && value !== '')
        .map(([key, value]) => `${formatMetricLabel(key)}: ${value}`)
        .join('，')
    })
    .filter(Boolean)
    .join('；')
}

const extractConfidence = (metrics) => {
  if (!metrics || typeof metrics !== 'object') {
    return null
  }

  if (typeof metrics.confidence === 'number' && !Number.isNaN(metrics.confidence)) {
    return Math.round(metrics.confidence)
  }

  if (Array.isArray(metrics.metrics)) {
    const confidenceValues = metrics.metrics
      .map((item) => item?.confidence)
      .filter((value) => typeof value === 'number' && !Number.isNaN(value))

    if (confidenceValues.length > 0) {
      const average = confidenceValues.reduce((sum, value) => sum + value, 0) / confidenceValues.length
      return Math.round(average)
    }
  }

  return null
}

const inferAnalysisType = (result) => {
  return result.imageType
    || result.metrics?.imageType
    || result.metrics?.type
    || '影像分析'
}

const normalizeAnalysisResult = (result) => {
  return {
    id: result.id,
    analysisType: inferAnalysisType(result),
    analysisDate: result.completedAt || result.createdAt,
    result: result.summary || summarizeMetrics(result.metrics) || result.errorMessage || '暂无分析摘要',
    confidence: extractConfidence(result.metrics),
    doctorDiagnosis: result.errorMessage || '',
  }
}

const formatConfidence = (value) => {
  return value == null ? '-' : `${value}%`
}

const loadPatientProfile = async () => {
  if (!patientId.value) {
    return
  }

  try {
    const patient = await patientApi.getPatient(patientId.value)
    patientName.value = patient?.name || patientName.value
    patientGender.value = formatGender(patient?.gender)
    patientAge.value = calculateAge(patient?.birthDate)
    patientBloodType.value = patient?.bloodType || '-'
    patientDoctor.value = patient?.attendingDoctorName || patient?.attendingDoctorId || '-'
    patientStatus.value = patient?.status || ''
    patientStatusText.value = formatPatientStatusText(patient?.status)
  } catch (err) {
    console.error('加载患者详情失败:', err)
  }
}

/**
 * 加载患者分析结果
 */
const loadAnalysisResults = async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await metricApi.getAnalysisResultsByPatientName(patientName.value)
    const results = Array.isArray(response) ? response : response?.results || []
    analysisResults.value = results.map(normalizeAnalysisResult)
  } catch (error) {
    console.error('加载分析结果失败:', error)
    analysisResults.value = []
    error.value = error.message || '加载分析结果失败，请重试'
  } finally {
    loading.value = false
  }
}

/**
 * 筛选结果
 */
const filterResults = () => {
  currentPage.value = 0 // 重置到第一页
}

/**
 * 切换页面
 * @param {number} newPage - 新页面索引
 */
const changePage = (newPage) => {
  currentPage.value = newPage
}

/**
 * 返回上一页
 */
const goBack = () => {
  router.back()
}

/**
 * 格式化日期
 * @param {string} dateString - 日期字符串
 * @returns {string} 格式化后的日期
 */
const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN')
}

// 组件挂载时加载数据
onMounted(() => {
  loadPatientProfile()
  loadAnalysisResults()
})
</script>

<style scoped>
.analysis-result-detail {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.nav-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1rem 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.nav-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.back-button {
  background: transparent;
  border: none;
  color: white;
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.back-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.back-icon {
  width: 20px;
  height: 20px;
}

.nav-content h2 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  flex: 1;
}

.patient-info {
  font-size: 0.9rem;
  opacity: 0.9;
}

.content-container {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.patient-profile {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 1.5rem;
  margin-bottom: 2rem;
}

.patient-profile h3 {
  margin: 0 0 1.5rem 0;
  color: #333;
  font-size: 1.2rem;
  font-weight: 600;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.profile-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.profile-item .label {
  font-size: 0.85rem;
  color: #666;
  font-weight: 500;
}

.profile-item .value {
  font-size: 0.95rem;
  color: #333;
  font-weight: 500;
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

.analysis-list {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 1.5rem;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.list-header h3 {
  margin: 0;
  color: #333;
  font-size: 1.2rem;
  font-weight: 600;
}

.filter-section {
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
}

.filter-select {
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  transition: border-color 0.3s;
}

.search-input {
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  transition: border-color 0.3s;
  width: 200px;
}

.filter-select:focus,
.search-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.loading,
.empty-state {
  text-align: center;
  padding: 4rem;
  color: #666;
  font-size: 1.1rem;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.empty-icon {
  width: 60px;
  height: 60px;
  color: #d9d9d9;
}

.result-cards {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.result-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  transition: all 0.3s;
}

.result-card:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #f0f0f0;
}

.card-header h4 {
  margin: 0;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.analysis-date {
  font-size: 0.85rem;
  color: #666;
  background-color: #f9f9f9;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
}

.card-content {
  margin-bottom: 1.25rem;
}

.analysis-result {
  margin-bottom: 1rem;
}

.analysis-result h5,
.doctor-diagnosis h5 {
  margin: 0 0 0.5rem 0;
  color: #555;
  font-size: 0.95rem;
  font-weight: 600;
}

.analysis-result p,
.doctor-diagnosis p {
  margin: 0;
  color: #333;
  line-height: 1.5;
  font-size: 0.95rem;
}

.confidence-score {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 0.5rem;
  background-color: #f9f9f9;
  border-radius: 4px;
}

.confidence-score .label {
  font-size: 0.9rem;
  color: #666;
  font-weight: 500;
}

.confidence-score .value {
  font-size: 0.95rem;
  color: #333;
  font-weight: 600;
  background-color: #e8f5e9;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
}

.doctor-diagnosis {
  border-top: 1px solid #f0f0f0;
  padding-top: 1rem;
}

.card-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
}

.btn-primary, .btn-secondary {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 4px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-primary {
  background-color: #667eea;
  color: white;
}

.btn-primary:hover {
  background-color: #5568d3;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-secondary {
  background-color: #f0f0f0;
  color: #333;
  border: 1px solid #ddd;
}

.btn-secondary:hover {
  background-color: #e0e0e0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e0e0e0;
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
  .content-container {
    padding: 1rem;
  }
  
  .nav-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .list-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .filter-section {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-input {
    width: 100%;
  }
  
  .profile-grid {
    grid-template-columns: 1fr;
  }
  
  .card-actions {
    flex-direction: column;
  }
  
  .btn-primary, .btn-secondary {
    width: 100%;
  }
  
  .pagination {
    flex-wrap: wrap;
  }
}
</style>
