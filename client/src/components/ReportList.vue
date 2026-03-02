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
        <button @click="generateNewReport" class="btn-primary">生成新报表</button>
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
            <button @click="generateNewReport" class="btn-primary">生成首个报表</button>
          </div>
        </div>
        <div v-else class="report-cards">
          <div v-for="report in filteredReports" :key="report.id" class="report-card">
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
                <span class="generated-by">生成人: {{ report.generatedBy }}</span>
                <span class="report-status" :class="report.status">{{ report.statusText }}</span>
              </div>
            </div>
            <div class="card-actions">
              <button @click="viewReport(report)" class="btn-secondary">查看</button>
              <button @click="downloadReport(report)" class="btn-secondary">下载</button>
              <button @click="printReport(report)" class="btn-secondary">打印</button>
              <button @click="regenerateReport(report)" class="btn-primary">重新生成</button>
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

const router = useRouter()
const route = useRoute()

// 患者信息
const patientId = ref(route.query.patientId || '')
const patientName = ref(route.query.patientName || '未知患者')
const generateReport = ref(route.query.generate === 'true')

// 报表相关状态
const reports = ref([])
const loading = ref(false)
const reportTypeFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(0)
const pageSize = ref(5)

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

/**
 * 加载患者报表
 */
const loadReports = async () => {
  loading.value = true
  
  try {
    // 模拟API调用
    // const result = await metricApi.getReportsByPatientId(patientId.value)
    // reports.value = result
    
    // 硬编码模拟数据
    reports.value = [
      {
        id: 1,
        reportTitle: '胸部X射线分析报告',
        reportType: '影像分析',
        reportDate: '2024-01-15',
        reportContent: '患者胸部X射线检查显示双肺纹理清晰，未见明显异常阴影。心脏大小正常，纵隔无增宽。肋骨结构完整。诊断意见：未见明显异常，建议定期复查。',
        generatedBy: '王医生',
        status: 'completed',
        statusText: '已完成'
      },
      {
        id: 2,
        reportTitle: '头部MRI检查报告',
        reportType: '影像分析',
        reportDate: '2024-01-10',
        reportContent: '患者头部MRI检查显示脑实质未见明显异常信号。脑室系统大小正常，脑沟裂无增宽。颅骨结构完整。诊断意见：头部MRI检查未见明显异常。',
        generatedBy: '李医生',
        status: 'completed',
        statusText: '已完成'
      },
      {
        id: 3,
        reportTitle: '综合诊断报告',
        reportType: '综合诊断',
        reportDate: '2024-01-05',
        reportContent: '患者经多项检查，未发现明显异常。建议保持健康生活方式，定期体检。',
        generatedBy: '王医生',
        status: 'completed',
        statusText: '已完成'
      },
      {
        id: 4,
        reportTitle: '随访报告',
        reportType: '随访报告',
        reportDate: '2023-12-20',
        reportContent: '患者随访情况良好，无不适症状。建议继续保持定期复查。',
        generatedBy: '赵医生',
        status: 'completed',
        statusText: '已完成'
      },
      {
        id: 5,
        reportTitle: '腹部超声检查报告',
        reportType: '影像分析',
        reportDate: '2023-12-10',
        reportContent: '患者腹部超声检查显示肝脏大小正常，回声均匀。胆囊无肿大，壁不厚。胰腺、脾脏大小正常。双肾结构清晰，无明显异常。诊断意见：腹部超声检查未见明显异常。',
        generatedBy: '王医生',
        status: 'completed',
        statusText: '已完成'
      }
    ]
  } catch (error) {
    console.error('加载报表失败:', error)
    alert('加载报表失败，请重试')
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
 * 生成新报表
 */
const generateNewReport = () => {
  // 模拟生成报表功能
  alert('生成新报表')
  
  // 模拟添加新报表到列表
  const newReport = {
    id: reports.value.length + 1,
    reportTitle: '新生成的报表',
    reportType: '综合诊断',
    reportDate: new Date().toISOString().split('T')[0],
    reportContent: '这是一份新生成的报表内容...',
    generatedBy: '当前医生',
    status: 'completed',
    statusText: '已完成'
  }
  
  reports.value.unshift(newReport)
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
  // 模拟查看报表功能
  alert(`查看报表: ${report.reportTitle}`)
}

/**
 * 下载报表
 * @param {Object} report - 报表对象
 */
const downloadReport = (report) => {
  // 模拟下载报表功能
  alert(`下载报表: ${report.reportTitle}`)
}

/**
 * 打印报表
 * @param {Object} report - 报表对象
 */
const printReport = (report) => {
  // 模拟打印报表功能
  alert(`打印报表: ${report.reportTitle}`)
}

/**
 * 重新生成报表
 * @param {Object} report - 报表对象
 */
const regenerateReport = (report) => {
  // 模拟重新生成报表功能
  alert(`重新生成报表: ${report.reportTitle}`)
  
  // 模拟更新报表日期
  report.reportDate = new Date().toISOString().split('T')[0]
  report.generatedBy = '当前医生'
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
  
  // 如果需要生成报表
  if (generateReport.value) {
    setTimeout(() => {
      generateNewReport()
    }, 1000)
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