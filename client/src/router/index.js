import { createRouter, createWebHistory } from 'vue-router'
import { authStore } from '../utils/auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../components/LoginForm.vue'),
    meta: { requiresAuth: false },
  },
  // 主页路由
  {
    path: '/home',
    name: 'Home',
    redirect: '/dashboard'
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../components/RegisterForm.vue'),
    meta: { requiresAuth: false },
  },
  // 主布局路由
  {
    path: '/',
    component: () => import('../components/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../components/Dashboard.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../components/UserProfile.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('../components/Notifications.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'help',
        name: 'HelpCenter',
        component: () => import('../components/HelpCenter.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'announcements',
        name: 'Announcements',
        component: () => import('../components/Announcements.vue'),
        meta: { requiresAuth: true },
      },
      // 管理员路由
      {
        path: 'manage/users',
        name: 'UserManage',
        component: () => import('../components/UserManage.vue'),
        meta: { requiresAuth: true, requiresRole: 'admin' },
      },
      {
        path: 'manage/hospitals',
        name: 'HospitalManage',
        component: () => import('../components/HospitalManage.vue'),
        meta: { requiresAuth: true, requiresRole: 'admin' },
      },
      // 用户路由
      {
        path: 'patients',
        name: 'PatientManage',
        component: () => import('../components/PatientManage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'patients/list',
        name: 'PatientList',
        component: () => import('../components/PatientList.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'reports',
        name: 'ReportManage',
        component: () => import('../components/ReportManage.vue'),
        meta: { requiresAuth: true },
      },
      // Metric模块路由
      {
        path: 'metric/images',
        name: 'MedicalImageManage',
        component: () => import('../components/MedicalImageManage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'metric/images/chat',
        name: 'MedicalImageChat',
        component: () => import('../components/MedicalImageChat.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'metric/analyses',
        name: 'AnalysisResultManage',
        component: () => import('../components/AnalysisResultManage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'metric/analyses/detail',
        name: 'AnalysisResultDetail',
        component: () => import('../components/AnalysisResultDetail.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'metric/reports',
        name: 'MetricReportManage',
        component: () => import('../components/MetricReportManage.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'metric/reports/list',
        name: 'ReportList',
        component: () => import('../components/ReportList.vue'),
        meta: { requiresAuth: true },
      },
    ]
  },
  // 兼容旧路由
  {
    path: '/users',
    redirect: '/manage/users'
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const isAuthenticated = authStore.isAuthenticated()

  if (requiresAuth && !isAuthenticated) {
    // 需要认证但未登录，跳转到登录页
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if ((to.name === 'Login' || to.name === 'Register') && isAuthenticated) {
    // 已登录用户访问登录/注册页，跳转到首页
    next({ name: 'Home' })
  } else if (to.meta.requiresRole) {
    // 检查角色权限
    const hasRole = authStore.hasRole(to.meta.requiresRole)
    if (hasRole) {
      next()
    } else {
      next({ name: 'Home' })
    }
  } else {
    next()
  }
})

export default router
