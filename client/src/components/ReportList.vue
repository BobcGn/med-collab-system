<template>
  <div class="report-list">
    <div class="nav-bar">
      <div class="nav-content">
        <button @click="goBack" class="back-button">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="back-icon">
            <path d="M19 12H5M12 19l-7-7 7-7"></path>
          </svg>
        </button>
        <h2>患者报表列表 - {{ patientName }}</h2>
        <div class="patient-info">
          <span class="patient-id">患者ID: {{ patientId }}</span>
        </div>
      </div>
    </div>

    <div class="content-container">
      <!-- 操作按钮 -->
      <div class="action-buttons">
        <button @click="refreshReports" class="btn-secondary">刷新列表</button>
      </div>

      <!-- 报表列表 -->
      <div class="report-container">
        <div class="list-header">
          <h3>报表列表</h3>
          <div class="filter-section">
            <select v-model="reportTypeFilter" @change="filterReports" class="filter-select">
              <option value="">全部类型</option>
              <option value="影像分析">影像分析</option>
              <option value="综合诊断">综合诊断</option>
              <option value="随访报告">随访报告</option>
              <option value="其他">其他</option>
            </select>
            <input 
              v-model="searchKeyword" 
              type="text" 
              placeholder="搜索报表..." 
              class="search-input"
              @input="filterReports"
            />
          </div>
        </div>

        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="error" class="empty-state">
          <div class="empty-content">
            <p>{{ error }}</p>
          </div>
        </div>
        <div v-else-if="filteredReports.length === 0" class="empty-state">
          <div class="empty-content">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
              <polyline points="10 9 9 9 8 9"></polyline>
            </svg>
            <p>暂无报表数据</p>
          </div>
        </div>
        <div v-else class="report-cards">
          <div v-for="report in paginatedReports" :key="report.id" class="report-card">
            <div class="card-header">
              <div class="header-info">
                <h4>{{ report.reportTitle }}</h4>
                <span class="report-type">{{ report.reportType }}</span>
              </div>
              <span class="report-date">{{ formatDate(report.reportDate) }}</span>
            </div>
            <div class="card-content">
              <p class="report-summary">{{ report.reportContent.substring(0, 150) }}...</p>
              <div class="report-meta">
                <span class="report-status" :class="report.status">{{ report.statusText }}</span>
              </div>
            </div>
            <div class="card-actions">
              <button @click="viewReport(report)" class="btn-secondary" :disabled="!report.filePath || activeFileReportId === report.id">
                {{ activeFileReportId === report.id ? '处理中...' : '查看' }}
              </button>
              <button @click="downloadReport(report)" class="btn-secondary" :disabled="!report.filePath || activeFileReportId === report.id">
                {{ activeFileReportId === report.id ? '处理中...' : '下载' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="!loading && filteredReports.length > 0" class="pagination">
          <button
            :disabled="currentPage === 0"
            @click="changePage(currentPage - 1)"
            class="page-button"
          >
            上一页
          </button>
          <span>第 {{ currentPage + 1 }} / {{ totalPages }} 页</span>
          <span>共 {{ filteredReports.length }} 条</span>
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
import { metricApi } from '../utils/api.js'

const router = useRouter()
const route = useRoute()

// 患者信息
const patientId = ref(route.query.patientId || '')
const patientName = ref(route.query.patientName || '未知患者')
const generateReport = ref(route.query.generate === 'true')

// 报表相关状态
const reports = ref([])
const loading = ref(false)
const error = ref('')
const reportTypeFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(0)
const pageSize = ref(5)
const activeFileReportId = ref('')

// 计算属性
const filteredReports = computed(() => {
  let results = [...reports.value]
  
  // 按类型筛选
  if (reportTypeFilter.value) {
    results = results.filter(report => report.reportType === reportTypeFilter.value)
  }
  
  // 按关键词搜索
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase()
    results = results.filter(report => 
      report.reportTitle.toLowerCase().includes(keyword) ||
      report.reportContent.toLowerCase().includes(keyword)
    )
  }
  
  return results
})

const totalPages = computed(() => {
  return Math.ceil(filteredReports.value.length / pageSize.value)
})

const paginatedReports = computed(() => {
  const start = currentPage.value * pageSize.value
  const end = start + pageSize.value
  return filteredReports.value.slice(start, end)
})

const formatReportStatus = (status) => {
  const statusMap = {
    completed: '已完成',
    generated: '已生成',
    pending: '处理中',
    failed: '失败',
  }
  return statusMap[status] || status || '未知状态'
}

const buildReportPreview = (report) => {
  if (report.errorMessage) {
    return `生成失败: ${report.errorMessage}`
  }
  if (report.filePath) {
    return `PDF 文件: ${report.filePath}`
  }
  if (Array.isArray(report.analysisIds) && report.analysisIds.length > 0) {
    return `已关联 ${report.analysisIds.length} 条分析结果`
  }
  return '暂无报表内容预览'
}

const extractFileNameFromPath = (filePath) => {
  if (!filePath) {
    return ''
  }
  return filePath.split('/').pop() || ''
}

const normalizeReport = (report) => {
  return {
    id: report.id,
    reportTitle: `${report.patientName || patientName.value} ${report.reportType || '报表'}`,
    reportType: report.reportType || '未分类',
    reportDate: report.generatedAt || report.createdAt,
    reportContent: buildReportPreview(report),
    status: report.status || 'unknown',
    statusText: formatReportStatus(report.status),
    filePath: report.filePath || '',
    fileName: extractFileNameFromPath(report.filePath),
  }
}

/**
 * 加载患者报表
 */
const loadReports = async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await metricApi.getReportsByPatientName(patientName.value)
    const resultList = Array.isArray(response) ? response : response?.reports || []
    reports.value = resultList.map(normalizeReport)
  } catch (error) {
    console.error('加载报表失败:', error)
    reports.value = []
    error.value = error.message || '加载报表失败，请重试'
  } finally {
    loading.value = false
  }
}

/**
 * 筛选报表
 */
const filterReports = () => {
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
 * 刷新报表列表
 */
const refreshReports = () => {
  loadReports()
}

/**
 * 查看报表
 * @param {Object} report - 报表对象
 */
const viewReport = (report) => {
  openReportFile(report, 'inline')
}

/**
 * 下载报表
 * @param {Object} report - 报表对象
 */
const downloadReport = (report) => {
  openReportFile(report, 'attachment')
}

const openReportFile = async (report, disposition) => {
  if (!report.filePath) {
    return
  }

  activeFileReportId.value = report.id
  try {
    const { blob, fileName } = await metricApi.getReportFile(report.id, disposition)
    const blobUrl = URL.createObjectURL(blob)

    if (disposition === 'attachment') {
      const link = document.createElement('a')
      link.href = blobUrl
      link.download = fileName || report.fileName || `${report.reportTitle}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
    } else {
      window.open(blobUrl, '_blank', 'noopener,noreferrer')
    }

    window.setTimeout(() => {
      URL.revokeObjectURL(blobUrl)
    }, 60_000)
  } catch (error) {
    console.error('获取报表文件失败:', error)
    window.alert(error.message || '获取报表文件失败，请稍后重试。')
  } finally {
    activeFileReportId.value = ''
  }
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
  loadReports()
  if (generateReport.value) {
    console.info('report generation flag detected, waiting for backend-generated reports')
  }
})
</script>

<style scoped>
.report-list {
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

.action-buttons {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
}

.btn-primary, .btn-secondary {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 0.95rem;
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

.report-container {
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
  gap: 1.5rem;
}

.empty-icon {
  width: 60px;
  height: 60px;
  color: #d9d9d9;
}

.report-cards {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.report-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.5rem;
  transition: all 0.3s;
}

.report-card:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #f0f0f0;
}

.header-info {
  flex: 1;
}

.header-info h4 {
  margin: 0 0 0.5rem 0;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.report-type {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  background-color: #e3f2fd;
  color: #1976d2;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
}

.report-date {
  font-size: 0.85rem;
  color: #666;
  background-color: #f9f9f9;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
}

.card-content {
  margin-bottom: 1.25rem;
}

.report-summary {
  margin: 0 0 1rem 0;
  color: #333;
  line-height: 1.5;
  font-size: 0.95rem;
}

.report-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.generated-by {
  font-size: 0.85rem;
  color: #666;
}

.report-status {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
}

.report-status.completed {
  background: #e8f5e9;
  color: #2e7d32;
}

.report-status.pending {
  background: #fff3e0;
  color: #e65100;
}

.report-status.error {
  background: #ffebee;
  color: #c62828;
}

.card-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.card-actions button {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 4px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
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
  
  .action-buttons {
    flex-direction: column;
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
  
  .card-actions {
    flex-direction: column;
  }
  
  .card-actions button {
    width: 100%;
  }
  
  .pagination {
    flex-wrap: wrap;
  }
}
</style>
