// 认证状态管理
const USER_KEY = 'currentUser'
const TOKEN_KEY = 'token'

export const authStore = {
  // 获取当前用户
  getCurrentUser() {
    const user = localStorage.getItem(USER_KEY)
    return user ? JSON.parse(user) : null
  },

  // 设置当前用户
  setCurrentUser(user) {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  },

  // 获取token
  getToken() {
    return localStorage.getItem(TOKEN_KEY)
  },

  // 设置token
  setToken(token) {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  },

  // 清除所有认证信息
  clearAuth() {
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(TOKEN_KEY)
  },

  // 检查是否已登录
  isAuthenticated() {
    return !!this.getToken()
  },

  // 检查用户角色
  hasRole(role) {
    const user = this.getCurrentUser()
    return user?.role === role
  },

  // 检查是否有任一角色
  hasAnyRole(roles) {
    const user = this.getCurrentUser()
    return user && roles.includes(user.role)
  },
}

// 角色常量
export const ROLES = {
  ADMIN: 'admin',
  DOCTOR: 'doctor',
  NURSE: 'nurse',
}
