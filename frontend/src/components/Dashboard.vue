<template>
  <div class="dashboard">
    <!-- 管理员端工作台 -->
    <div v-if="isAdmin" class="admin-dashboard">
      <h2 class="dashboard-title">管理员工作台</h2>
      
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon user-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalUsers }}</div>
            <div class="stat-label">总用户数</div>
            <div class="stat-change" :class="{ positive: stats.userChange > 0, negative: stats.userChange < 0 }">
              {{ stats.userChange > 0 ? '+' : '' }}{{ stats.userChange }}%
              <span class="change-text">较上周</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon hospital-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 21h18"></path>
              <path d="M5 21V7l8-4 8 4v14"></path>
              <path d="M8 1v4"></path>
              <path d="M16 1v4"></path>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalHospitals }}</div>
            <div class="stat-label">医院数量</div>
            <div class="stat-change positive">
              +{{ stats.newHospitals }}
              <span class="change-text">本月新增</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon department-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalDepartments }}</div>
            <div class="stat-label">科室数量</div>
            <div class="stat-change positive">
              {{ stats.departmentsPerHospital }}
              <span class="change-text">平均每院</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon alert-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingApprovals }}</div>
            <div class="stat-label">待审批</div>
            <div class="stat-change" :class="{ positive: stats.pendingApprovals === 0 }">
              {{ stats.pendingApprovals === 0 ? '无' : '需处理' }}
              <span class="change-text">申请</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 最近活动 -->
      <div class="recent-activities">
        <h3 class="section-title">最近活动</h3>
        <div class="activity-list">
          <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
            <div class="activity-icon" :class="activity.type">
              <component :is="getActivityIcon(activity.type)" />
            </div>
            <div class="activity-content">
              <div class="activity-title">{{ activity.title }}</div>
              <div class="activity-time">{{ activity.time }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 用户端工作台 -->
    <div v-else class="user-dashboard">
      <h2 class="dashboard-title">{{ getRoleTitle() }}</h2>
      
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon patient-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.todayPatients }}</div>
            <div class="stat-label">今日患者</div>
            <div class="stat-change positive">
              {{ stats.patientIncrease }}%
              <span class="change-text">较昨日</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon report-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
              <polyline points="10 9 9 9 8 9"></polyline>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.todayReports }}</div>
            <div class="stat-label">今日报表</div>
            <div class="stat-change positive">
              {{ stats.reportIncrease }}%
              <span class="change-text">较昨日</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon pending-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingTasks }}</div>
            <div class="stat-label">待处理</div>
            <div class="stat-change">
              {{ stats.pendingTasks }}
              <span class="change-text">项任务</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon complete-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.completedTasks }}</div>
            <div class="stat-label">已完成</div>
            <div class="stat-change positive">
              {{ stats.completionRate }}%
              <span class="change-text">完成率</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷操作 -->
      <div class="quick-actions">
        <h3 class="section-title">快捷操作</h3>
        <div class="action-grid">
          <div class="action-card" @click="quickAction('new-patient')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="8.5" cy="7" r="4"></circle>
              <line x1="20" y1="8" x2="20" y2="14"></line>
              <line x1="23" y1="11" x2="17" y2="11"></line>
            </svg>
            <span>新增患者</span>
          </div>
          <div class="action-card" @click="quickAction('new-report')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="12" y1="18" x2="12" y2="12"></line>
              <line x1="9" y1="15" x2="15" y2="15"></line>
            </svg>
            <span>生成报表</span>
          </div>
          <div class="action-card" @click="quickAction('patient-list')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
            </svg>
            <span>患者列表</span>
          </div>
          <div class="action-card" @click="quickAction('report-history')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <span>历史报表</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../utils/auth'

const router = useRouter()
const currentUser = authStore.getCurrentUser()

const isAdmin = computed(() => authStore.hasRole('admin'))

const stats = ref({
  // 管理员统计
  totalUsers: 156,
  userChange: 5.2,
  totalHospitals: 12,
  newHospitals: 2,
  totalDepartments: 45,
  departmentsPerHospital: 3.8,
  pendingApprovals: 3,
  // 用户统计
  todayPatients: 28,
  patientIncrease: 12.5,
  todayReports: 15,
  reportIncrease: 8.3,
  pendingTasks: 7,
  completedTasks: 23,
  completionRate: 76.7
})

const recentActivities = ref([
  { id: 1, type: 'user', title: '新增用户：张医生', time: '10分钟前' },
  { id: 2, type: 'hospital', title: '新增医院：市中心医院', time: '1小时前' },
  { id: 3, type: 'report', title: '生成报表：月度统计报告', time: '2小时前' },
  { id: 4, type: 'alert', title: '系统更新通知', time: '3小时前' },
  { id: 5, type: 'user', title: '用户角色变更：李医生', time: '5小时前' }
])

const getRoleTitle = () => {
  const role = currentUser?.role
  const roleMap = {
    doctor: '医生工作台',
    nurse: '护士工作台',
    receptionist: '前台工作台'
  }
  return roleMap[role] || '工作台'
}

const getActivityIcon = (type) => {
  const icons = {
    user: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>`,
    hospital: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 21h18"></path><path d="M5 21V7l8-4 8 4v14"></path></svg>`,
    report: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>`,
    alert: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`
  }
  return icons[type] || icons.user
}

const quickAction = (action) => {
  const actionMap = {
    'new-patient': '/patients/new',
    'new-report': '/reports/generate',
    'patient-list': '/patients',
    'report-history': '/reports/history'
  }
  if (actionMap[action]) {
    router.push(actionMap[action])
  }
}

onMounted(() => {
  // 这里可以从后端API获取真实数据
  console.log('加载工作台数据...')
})
</script>

<style scoped>
.dashboard {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dashboard-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 1.5rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  gap: 1.5rem;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 32px;
  height: 32px;
}

.user-icon { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.user-icon svg { color: white; }

.hospital-icon { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
.hospital-icon svg { color: white; }

.department-icon { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.department-icon svg { color: white; }

.alert-icon { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); }
.alert-icon svg { color: white; }

.patient-icon { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.patient-icon svg { color: white; }

.report-icon { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); }
.report-icon svg { color: white; }

.pending-icon { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); }
.pending-icon svg { color: white; }

.complete-icon { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.complete-icon svg { color: white; }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 2rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 0.25rem;
}

.stat-label {
  font-size: 0.9rem;
  color: #666;
  margin-bottom: 0.5rem;
}

.stat-change {
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.stat-change.positive {
  color: #52c41a;
}

.stat-change.negative {
  color: #ff4d4f;
}

.change-text {
  color: #999;
}

.recent-activities,
.quick-actions {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 1rem;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border-radius: 8px;
  background: #fafafa;
  transition: all 0.3s;
}

.activity-item:hover {
  background: #f3f4ff;
}

.activity-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.activity-icon.user { background: #e6f7ff; }
.activity-icon.hospital { background: #fff1f0; }
.activity-icon.report { background: #f6ffed; }
.activity-icon.alert { background: #fffbe6; }

.activity-icon svg {
  width: 20px;
  height: 20px;
  color: #4c6fff;
}

.activity-content {
  flex: 1;
}

.activity-title {
  font-size: 0.95rem;
  color: #333;
  margin-bottom: 0.25rem;
}

.activity-time {
  font-size: 0.85rem;
  color: #999;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.action-card {
  padding: 1.5rem;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.action-card:hover {
  background: #f3f4ff;
  transform: translateY(-2px);
}

.action-card svg {
  width: 40px;
  height: 40px;
  color: #4c6fff;
}

.action-card span {
  font-size: 0.9rem;
  color: #333;
  font-weight: 500;
}
</style>
