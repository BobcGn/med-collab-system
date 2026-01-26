// API 基础配置
const API_BASE_URL = 'http://localhost:8088/api/auth'

// 请求拦截器
const request = async (url, options = {}) => {
  const token = localStorage.getItem('token')

  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    },
  }

  try {
    const response = await fetch(`${API_BASE_URL}${url}`, config)

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败' }))
      throw new Error(error.message || '请求失败')
    }

    return await response.json()
  } catch (error) {
    throw error
  }
}

// 认证相关 API
export const authApi = {
  // 用户注册
  register: (data) => request('/register', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 用户登录
  login: (data) => request('/login', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 获取当前用户信息
  getCurrentUser: () => request('/user', {
    method: 'GET',
  }),

  // 刷新token
  refreshToken: () => request('/refresh', {
    method: 'POST',
  }),

  // 获取指定用户信息
  getUserById: (id) => request(`/users/${id}`, {
    method: 'GET',
  }),

  // 批量获取用户信息
  getUsersByIds: (userIds) => request('/users/batch', {
    method: 'POST',
    body: JSON.stringify(userIds),
  }),

  // 获取所有用户（分页）
  getAllUsers: (params = {}) => {
    const query = new URLSearchParams(params).toString()
    return request(`/users${query ? `?${query}` : ''}`, {
      method: 'GET',
    })
  },

  // 搜索用户
  searchUsers: (keyword) => request(`/users/search?keyword=${encodeURIComponent(keyword)}`, {
    method: 'GET',
  }),

  // 获取科室医生列表
  getDepartmentDoctors: (hospitalId, deptCode) => request(`/users/doctors/${hospitalId}/${deptCode}`, {
    method: 'GET',
  }),

  // 获取科室护士列表
  getDepartmentNurses: (hospitalId, deptCode) => request(`/users/nurses/${hospitalId}/${deptCode}`, {
    method: 'GET',
  }),

  // 获取医院所有用户
  getHospitalUsers: (hospitalId) => request(`/users/hospital/${hospitalId}`, {
    method: 'GET',
  }),

  // 更新用户信息
  updateUser: (id, data) => request(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 修改密码
  changePassword: (data) => request('/password', {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 重置密码（管理员功能）
  resetPassword: (id, newPassword) => request(`/users/${id}/password`, {
    method: 'PUT',
    body: JSON.stringify({ newPassword }),
  }),

  // 修改用户名
  changeUsername: (newUsername) => request('/username', {
    method: 'PUT',
    body: JSON.stringify({ newUsername }),
  }),

  // 更新用户角色
  changeUserRole: (id, newRole) => request(`/users/${id}/role`, {
    method: 'PUT',
    body: JSON.stringify({ newRole }),
  }),

  // 删除用户
  deleteUser: (id) => request(`/users/${id}`, {
    method: 'DELETE',
  }),

  // 批量删除用户
  batchDeleteUsers: (userIds) => request('/users/batch', {
    method: 'DELETE',
    body: JSON.stringify(userIds),
  }),

  // 更新用户基本信息
  updateUserInfo: (id, data) => request(`/users/${id}/info`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 获取用户统计信息
  getUserStatistics: (hospitalId) => {
    const query = hospitalId ? `?hospitalId=${hospitalId}` : ''
    return request(`/statistics${query}`, {
      method: 'GET',
    })
  },

  // 验证Token并获取用户信息
  validateToken: (token) => request('/validate', {
    method: 'POST',
    body: JSON.stringify({ token }),
  }),
}
