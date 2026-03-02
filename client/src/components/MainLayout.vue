<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <header class="top-header">
      <div class="header-left">
        <h1 class="logo">医工协同创新平台</h1>
      </div>

      <nav class="header-nav">
        <div class="nav-item" @click="navigateTo('/notifications')">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
            <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
          </svg>
          <span>消息通知</span>
          <span v-if="notificationCount > 0" class="badge">{{ notificationCount }}</span>
        </div>

        <div class="nav-item" @click="navigateTo('/help')">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <span>帮助中心</span>
        </div>

        <div class="nav-item" @click="navigateTo('/profile')">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </svg>
          <span>个人中心</span>
        </div>

        <div class="nav-item" @click="navigateTo('/announcements')">
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
          </svg>
          <span>系统公告</span>
        </div>
      </nav>

      <div class="header-right">
        <div class="user-info">
          <span>{{ currentUser?.fullName || '用户' }}</span>
          <span class="user-role">{{ getRoleName(currentUser?.role) }}</span>
        </div>
        <button @click="handleLogout" class="logout-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
            <polyline points="16 17 21 12 16 7"></polyline>
            <line x1="21" y1="12" x2="9" y2="12"></line>
          </svg>
          退出
        </button>
      </div>
    </header>

    <div class="content-wrapper">
      <!-- 左侧边栏 -->
      <aside class="sidebar">
        <div class="sidebar-content">
          <!-- 管理员端菜单 -->
          <div v-if="isAdmin" class="menu-section">
            <div class="menu-item" :class="{ active: currentRoute === '/dashboard' }" @click="navigateTo('/dashboard')">
              <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7"></rect>
                <rect x="14" y="3" width="7" height="7"></rect>
                <rect x="14" y="14" width="7" height="7"></rect>
                <rect x="3" y="14" width="7" height="7"></rect>
              </svg>
              <span>工作台</span>
            </div>

            <!-- 用户管理（可展开） -->
            <div class="menu-group">
              <div class="menu-item" :class="{ active: isUserManagementActive }" @click="toggleUserManagement">
                <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                <span>用户管理</span>
                <svg v-if="!userManagementExpanded" class="expand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
                <svg v-else class="expand-icon rotate" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
              </div>
              <div v-show="userManagementExpanded" class="submenu">
                <div class="submenu-item" :class="{ active: currentRoute === '/manage/users' }" @click="navigateTo('/manage/users')">
                  <span>用户管理</span>
                </div>
                <div class="submenu-item" :class="{ active: currentRoute === '/users' }" @click="navigateTo('/users')">
                  <span>用户列表</span>
                </div>
              </div>
            </div>

            <!-- 医院科室管理 -->
            <div class="menu-item" :class="{ active: currentRoute === '/manage/hospitals' || currentRoute.startsWith('/manage/departments') }" @click="navigateTo('/manage/hospitals')">
              <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 21h18"></path>
                <path d="M5 21V7l8-4 8 4v14"></path>
                <path d="M8 1v4"></path>
                <path d="M16 1v4"></path>
              </svg>
              <span>医院科室管理</span>
            </div>
          </div>

          <!-- 用户端菜单 -->
          <div v-else class="menu-section">
            <div class="menu-item" :class="{ active: currentRoute === '/dashboard' }" @click="navigateTo('/dashboard')">
              <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="7" height="7"></rect>
                <rect x="14" y="3" width="7" height="7"></rect>
                <rect x="14" y="14" width="7" height="7"></rect>
                <rect x="3" y="14" width="7" height="7"></rect>
              </svg>
              <span>工作台</span>
            </div>

            <!-- 患者管理（可展开） -->
            <div class="menu-group">
              <div class="menu-item" :class="{ active: isPatientManagementActive }" @click="togglePatientManagement">
                <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
                <span>患者管理</span>
                <svg v-if="!patientManagementExpanded" class="expand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
                <svg v-else class="expand-icon rotate" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
              </div>
              <div v-show="patientManagementExpanded" class="submenu">
                <div class="submenu-item" :class="{ active: currentRoute === '/patients' }" @click="navigateTo('/patients')">
                  <span>患者管理</span>
                </div>
                <div class="submenu-item" :class="{ active: currentRoute === '/patients/list' }" @click="navigateTo('/patients/list')">
                  <span>患者列表</span>
                </div>
              </div>
            </div>



            <!-- Metric模块（可展开） -->
            <div class="menu-group">
              <div class="menu-item" :class="{ active: isMetricManagementActive }" @click="toggleMetricManagement">
                <svg class="menu-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>
                </svg>
                <span>Metric管理</span>
                <svg v-if="!metricManagementExpanded" class="expand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
                <svg v-else class="expand-icon rotate" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
              </div>
              <div v-show="metricManagementExpanded" class="submenu">
                <div class="submenu-item" :class="{ active: currentRoute === '/metric/images' }" @click="navigateTo('/metric/images')">
                  <span>医学影像管理</span>
                </div>
                <div class="submenu-item" :class="{ active: currentRoute === '/metric/analyses' }" @click="navigateTo('/metric/analyses')">
                  <span>分析结果管理</span>
                </div>
                <div class="submenu-item" :class="{ active: currentRoute === '/metric/reports' }" @click="navigateTo('/metric/reports')">
                  <span>Metric报表管理</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 主内容区域 -->
      <main class="main-content">
        <!-- 科室和时间信息栏 -->
        <div class="info-bar">
          <div class="info-item department-info">
            <svg class="info-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 21h18"></path>
              <path d="M5 21V7l8-4 8 4v14"></path>
              <path d="M8 1v4"></path>
              <path d="M16 1v4"></path>
            </svg>
            <span>{{ departmentInfo }}</span>
          </div>
          <div class="info-item time-info">
            <svg class="info-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <span>{{ currentTime }}</span>
          </div>
        </div>

        <!-- 路由内容 -->
        <div class="content-container">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authStore } from '../utils/auth.js'

const router = useRouter()
const route = useRoute()

// 菜单展开状态
const userManagementExpanded = ref(false)
const patientManagementExpanded = ref(false)

const metricManagementExpanded = ref(false)

// 通知数量
const notificationCount = ref(3)

// 当前时间
const currentTime = ref('')
let timeInterval = null

// 计算属性
const currentUser = computed(() => authStore.getCurrentUser())
const isAdmin = computed(() => authStore.hasRole('admin'))

const currentRoute = computed(() => route.path)

const isUserManagementActive = computed(() => {
  return route.path.startsWith('/manage/users') || route.path === '/users'
})

const isPatientManagementActive = computed(() => {
  return route.path.startsWith('/patients')
})



const isMetricManagementActive = computed(() => {
  return route.path.startsWith('/metric')
})

const departmentInfo = computed(() => {
  const user = currentUser.value
  
  if (!user) return '未登录'
  // 优先使用医院名称和科室名称
  if (user.hospitalName || user.deptName) {
    return `${user.hospitalName || ''}${user.hospitalName && user.deptName ? ' - ' : ''}${user.deptName || ''}`
  }
  // 如果没有名称，回退到使用ID
  if (user.hospitalId || user.deptCode) {
    return `${user.hospitalId || ''}${user.hospitalId && user.deptCode ? ' - ' : ''}${user.deptCode || ''}`
  }
  // 只有当用户是管理员且没有医院信息时，才显示"系统管理员"
  if (user.role === 'admin') {
    return '系统管理员'
  }
  return '未分配部门'
})

// 获取角色名称
const getRoleName = (role) => {
  const roleMap = {
    admin: '管理员',
    doctor: '医生',
    nurse: '护士',
    receptionist: '前台'
  }
  return roleMap[role] || role
}

// 导航方法
const navigateTo = (path) => {
  router.push(path)
}

const toggleUserManagement = () => {
  userManagementExpanded.value = !userManagementExpanded.value
}

const togglePatientManagement = () => {
  patientManagementExpanded.value = !patientManagementExpanded.value
}



const toggleMetricManagement = () => {
  metricManagementExpanded.value = !metricManagementExpanded.value
}

// 退出登录
const handleLogout = () => {
  authStore.clearAuth()
  router.push('/login')
}

// 更新时间
const updateTime = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${year}-${month}-${day}  ${hours}:${minutes}:${seconds}`
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

/* 顶部导航栏 */
.top-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #ffffff;
  padding: 0.75rem 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left .logo {
  font-size: 1.4rem;
  font-weight: 600;
  color: #4c6fff;
  letter-spacing: 0.08em;
  margin: 0;
}

.header-nav {
  display: flex;
  gap: 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  color: #666;
  font-size: 0.9rem;
  font-weight: 500;
}

.nav-item:hover {
  background: #f3f4ff;
  color: #4c6fff;
}

.nav-icon {
  width: 20px;
  height: 20px;
}

.badge {
  position: absolute;
  top: -2px;
  right: -2px;
  background: #ff4d4f;
  color: white;
  font-size: 0.7rem;
  padding: 0 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
  line-height: 1.2;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.user-role {
  background: #f3f4ff;
  color: #4c6fff;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.5rem 1rem;
  background: #fff3e6;
  color: #ff9800;
  border: 1px solid #ffd080;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  transition: all 0.3s;
}

.logout-btn:hover {
  background: #ff9800;
  color: white;
}

.logout-btn svg {
  width: 18px;
  height: 18px;
}

/* 内容区域 */
.content-wrapper {
  display: flex;
  flex: 1;
  min-height: calc(100vh - 64px);
}

/* 左侧边栏 */
.sidebar {
  width: 240px;
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;
}

.sidebar-content {
  padding: 1rem 0;
}

.menu-section {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1.5rem;
  cursor: pointer;
  transition: all 0.3s;
  color: #666;
  font-size: 0.95rem;
  font-weight: 500;
  position: relative;
}

.menu-item:hover {
  background: #f3f4ff;
  color: #4c6fff;
}

.menu-item.active {
  background: #f3f4ff;
  color: #4c6fff;
  border-right: 3px solid #4c6fff;
}

.menu-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.expand-icon {
  width: 16px;
  height: 16px;
  margin-left: auto;
  transition: transform 0.3s;
}

.expand-icon.rotate {
  transform: rotate(90deg);
}

.menu-group {
  display: flex;
  flex-direction: column;
}

.submenu {
  background: #fafafa;
  padding-left: 0.5rem;
}

.submenu-item {
  padding: 0.75rem 1.5rem 0.75rem 3rem;
  cursor: pointer;
  transition: all 0.3s;
  color: #666;
  font-size: 0.9rem;
}

.submenu-item:hover {
  background: #f3f4ff;
  color: #4c6fff;
}

.submenu-item.active {
  background: #f3f4ff;
  color: #4c6fff;
  font-weight: 600;
}

/* 主内容区域 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
}

.info-bar {
  background: #ffffff;
  padding: 0.75rem 2rem;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: #666;
  font-weight: 500;
}

.info-icon {
  width: 18px;
  height: 18px;
  color: #4c6fff;
}

.content-container {
  flex: 1;
  padding: 1.5rem 2rem;
  overflow-y: auto;
}

/* 滚动条样式 */
.sidebar::-webkit-scrollbar {
  width: 6px;
}

.sidebar::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.sidebar::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.sidebar::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}
</style>
