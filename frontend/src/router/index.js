import { createRouter, createWebHistory } from 'vue-router'
import { authStore } from '../utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../components/LoginForm.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../components/RegisterForm.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../components/UserProfile.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('../components/UserList.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/manage/users',
    name: 'UserManage',
    component: () => import('../components/UserManage.vue'),
    meta: { requiresAuth: true, requiresRole: 'admin' },
  },
  {
    path: '/manage/hospitals',
    name: 'HospitalManage',
    component: () => import('../components/HospitalManage.vue'),
    meta: { requiresAuth: true, requiresRole: 'admin' },
  },
  {
    path: '/',
    name: 'Home',
    redirect: '/profile',
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, from, next) => {
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
