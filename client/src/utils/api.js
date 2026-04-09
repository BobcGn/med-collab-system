import { getGatewayOrigin } from './runtimeConfig'

// API 基础配置
const GATEWAY_ORIGIN = getGatewayOrigin()
const API_BASE_URL = `${GATEWAY_ORIGIN}/api/auth`

// 请求拦截器
const request = async (url, options = {}) => {
  const token = localStorage.getItem('token')

  const baseHeaders = {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` }),
  }

  const config = {
    ...options,
    headers: {
      ...baseHeaders,
      ...options.headers,
    },
  }

  // 内部重试标记，防止无限循环
  let hasRetry = false

  // 执行请求并在遇到 401 时尝试刷新令牌
  const doRequest = async () => {
    const response = await fetch(`${API_BASE_URL}${url}`, config)

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败' }))

      // 401 时尝试刷新令牌一次，然后重试
      if (response.status === 401 && !hasRetry && token) {
        try {
          // 先尝试用当前 token 刷新 Token
          const refreshResp = await fetch(`${API_BASE_URL}/refresh`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`,
            },
          })

          if (refreshResp.ok) {
            const refreshData = await refreshResp.json()
            const newToken = refreshData?.token
            if (newToken) {
              localStorage.setItem('token', newToken)
              // 替换 header 中的 token，然后重试
              config.headers = {
                ...config.headers,
                Authorization: `Bearer ${newToken}`,
              }
              hasRetry = true
              return await doRequest()
            }
          } else {
            // 刷新 Token 失败，说明当前 Token 已完全无效，跳转到登录页
            console.error('[API] 刷新 Token 失败，跳转到登录页')
            // 清除认证信息
            localStorage.removeItem('token')
            localStorage.removeItem('currentUser')
            // 跳转到登录页
            window.location.href = '/login'
            throw new Error('登录已过期，请重新登录')
          }
        } catch (e) {
          // 刷新 Token 时发生错误，说明当前 Token 已完全无效，跳转到登录页
          console.error('[API] 刷新 Token 时发生错误，跳转到登录页:', e)
          // 清除认证信息
          localStorage.removeItem('token')
          localStorage.removeItem('currentUser')
          // 跳转到登录页
          window.location.href = '/login'
          throw new Error('登录已过期，请重新登录')
        }
      }

      throw new Error(error.message || `请求失败 (${response.status})`)
    }

    // 处理空响应体的情况
    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      // 如果响应体不是JSON，返回空数组
      console.log('[Patient API] 响应体不是JSON，返回空数组')
      return []
    }

    // 尝试解析响应体
    try {
      // 先检查响应体是否为空
      const text = await response.text()
      if (!text) {
        // 如果响应体为空，返回空数组
        console.log('[Patient API] 响应体为空，返回空数组')
        return []
      }
      // 如果响应体不为空，尝试解析它
      const data = JSON.parse(text)
      console.log('[Patient API] 响应体解析成功:', data)
      return data
    } catch (e) {
      // 如果解析失败，返回空数组
      console.log('[Patient API] 响应体解析失败，返回空数组:', e)
      return []
    }
  }

  try {
    return await doRequest()
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

  // ============ 医院相关 API ============
  // 获取所有医院
  getAllHospitals: (includeInactive = false) => {
    const query = includeInactive ? '?includeInactive=true' : ''
    return request(`/hospitals${query}`, {
      method: 'GET',
    })
  },

  // 获取指定医院信息
  getHospitalById: (id) => request(`/hospitals/${id}`, {
    method: 'GET',
  }),

  // 创建医院
  createHospital: (data) => request('/hospitals', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 更新医院
  updateHospital: (id, data) => request(`/hospitals/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 删除医院
  deleteHospital: (id) => request(`/hospitals/${id}`, {
    method: 'DELETE',
  }),

  // ============ 部门相关 API ============
  // 获取指定医院的所有部门
  getHospitalDepartments: (hospitalId, includeInactive = false) => {
    const query = includeInactive ? '?includeInactive=true' : ''
    return request(`/hospitals/${hospitalId}/departments${query}`, {
      method: 'GET',
    })
  },

  // 获取指定部门信息
  getDepartmentById: (id) => request(`/departments/${id}`, {
    method: 'GET',
  }),

  // 创建部门
  createDepartment: (data) => request('/departments', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 更新部门
  updateDepartment: (id, data) => request(`/departments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 删除部门
  deleteDepartment: (id) => request(`/departments/${id}`, {
    method: 'DELETE',
  }),
}

// 患者服务 API 配置（通过网关访问）
const PATIENT_API_BASE_URL = GATEWAY_ORIGIN

const patientRequest = async (url, options = {}) => {
  const token = localStorage.getItem('token')

  // 修正URL构建逻辑 - 处理不同URL格式
  let fullUrl
  if (url.startsWith('/')) {
    // 以/开头的路径
    fullUrl = `${PATIENT_API_BASE_URL}${url}`
  } else if (url.startsWith('?')) {
    // 以?开头的查询参数
    fullUrl = `${PATIENT_API_BASE_URL}${url}`
  } else if (url) {
    // 其他非空路径
    fullUrl = `${PATIENT_API_BASE_URL}/${url}`
  } else {
    // 空路径
    fullUrl = PATIENT_API_BASE_URL
  }

  console.log('[Patient API] 请求URL:', fullUrl)
  console.log('[Patient API] Token存在:', !!token)
  console.log('[Patient API] Token前缀:', token?.substring(0, 20))

  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    },
  }

  // 记录请求体(仅用于调试)
  if (options.body) {
    try {
      const requestBody = JSON.parse(options.body)
      console.log('[Patient API] 请求体数据:', JSON.stringify(requestBody, null, 2))
    } catch (e) {
      console.log('[Patient API] 请求体原始数据:', options.body)
    }
  }

  console.log('[Patient API] 请求头Authorization:', config.headers.Authorization ? config.headers.Authorization.substring(0, 30) : '未设置')

  // 请求患者数据，若返回 401/502/503，则尝试刷新 Token 或重试一次后再报错
  let retryPatient = false
  let retry502 = false
  try {
    let response = await fetch(fullUrl, config)

    console.log('[Patient API] 响应状态:', response.status)
    console.log('[Patient API] 响应OK:', response.ok)

    // 如果服务端返回 502/503，尝试一次简单重试以应对短暂网关问题
    if (!response.ok && (response.status === 502 || response.status === 503) && !retry502) {
      retry502 = true
      console.warn('[Patient API] 收到后端网关错误 (502/503)，尝试重试一次')
      response = await fetch(fullUrl, config)
      console.log('[Patient API] 重试后响应状态:', response.status)
      console.log('[Patient API] 重试后响应OK:', response.ok)
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败', code: response.status }))
      console.error('[Patient API] 请求失败，详细信息:', {
        status: response.status,
        url: fullUrl,
        error: error,
        requestBody: options.body
      })
      // 401 时尝试自动刷新 Token
      if (response.status === 401 && token && !retryPatient) {
        try {
          const refreshResp = await fetch(`${API_BASE_URL}/refresh`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`,
            },
          })
          if (refreshResp.ok) {
            const refreshData = await refreshResp.json()
            const newToken = refreshData?.token
            if (newToken) {
              localStorage.setItem('token', newToken)
              // 更新请求头中的 token，然后重试
              config.headers = {
                ...config.headers,
                Authorization: `Bearer ${newToken}`,
              }
              retryPatient = true
              response = await fetch(fullUrl, config)
              if (!response.ok) {
                const err2 = await response.json().catch(() => ({ message: '请求失败', code: response.status }))
                console.error('[Patient API] 重试请求失败，详细信息:', {
                  status: response.status,
                  url: fullUrl,
                  error: err2
                })
                throw new Error(err2.message || `请求失败 (${response.status})`)
              }
            }
          } else {
            // 刷新 Token 失败，说明当前 Token 已完全无效，跳转到登录页
            console.error('[Patient API] 刷新 Token 失败，跳转到登录页')
            // 清除认证信息
            localStorage.removeItem('token')
            localStorage.removeItem('currentUser')
            // 跳转到登录页
            window.location.href = '/login'
            throw new Error('登录已过期，请重新登录')
          }
        } catch (e) {
          // 刞新 Token 时发生错误，说明当前 Token 已完全无效，跳转到登录页
          console.error('[Patient API] 刷新 Token 时发生错误，跳转到登录页:', e)
          // 清除认证信息
          localStorage.removeItem('token')
          localStorage.removeItem('currentUser')
          // 跳转到登录页
          window.location.href = '/login'
          throw new Error('登录已过期，请重新登录')
        }
      }
      // 构建详细的错误信息
      const errorMessage = error.message || error.error || error.detail || `请求失败 (${response.status})`
      const detailedError = `${errorMessage} (状态码: ${response.status})`
      console.error('[Patient API] 详细错误信息:', detailedError)
      throw new Error(detailedError)
    }

    // 处理空响应体的情况
    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      // 如果响应体不是JSON，返回空数组
      console.log('[Patient API] 响应体不是JSON，返回空数组')
      return []
    }

    // 尝试解析响应体
    try {
      // 先检查响应体是否为空
      const text = await response.text()
      if (!text) {
        // 如果响应体为空，返回空数组
        console.log('[Patient API] 响应体为空，返回空数组')
        return []
      }
      // 如果响应体不为空，尝试解析它
      const data = JSON.parse(text)
      console.log('[Patient API] 响应体解析成功:', data)
      return data
    } catch (e) {
      // 如果解析失败，返回空数组
      console.log('[Patient API] 响应体解析失败，返回空数组:', e)
      return []
    }
  } catch (error) {
    console.error('患者API请求异常:', error)
    throw error
  }
}

// Metric服务 API 配置（通过网关访问）
const METRIC_API_BASE_URL = GATEWAY_ORIGIN

const metricRequest = async (url, options = {}) => {
  const token = localStorage.getItem('token')

  // 修正URL构建逻辑 - 处理不同URL格式
  let fullUrl
  if (url.startsWith('/')) {
    // 以/开头的路径
    fullUrl = `${METRIC_API_BASE_URL}${url}`
  } else if (url.startsWith('?')) {
    // 以?开头的查询参数
    fullUrl = `${METRIC_API_BASE_URL}${url}`
  } else if (url) {
    // 其他非空路径
    fullUrl = `${METRIC_API_BASE_URL}/${url}`
  } else {
    // 空路径
    fullUrl = METRIC_API_BASE_URL
  }

  console.log('[Metric API] 请求URL:', fullUrl)
  console.log('[Metric API] Token存在:', !!token)
  console.log('[Metric API] Token前缀:', token?.substring(0, 20))

  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    },
  }

  // 记录请求体(仅用于调试)
  if (options.body) {
    try {
      const requestBody = JSON.parse(options.body)
      console.log('[Metric API] 请求体数据:', JSON.stringify(requestBody, null, 2))
    } catch (e) {
      console.log('[Metric API] 请求体原始数据:', options.body)
    }
  }

  console.log('[Metric API] 请求头Authorization:', config.headers.Authorization ? config.headers.Authorization.substring(0, 30) : '未设置')
  console.log('[Metric API] 完整请求头:', config.headers)

  // 请求metric数据，若返回 401/502/503，则尝试刷新 Token 或重试一次后再报错
  let retryMetric = false
  let retry502 = false
  try {
    let response = await fetch(fullUrl, config)

    console.log('[Metric API] 响应状态:', response.status)
    console.log('[Metric API] 响应OK:', response.ok)

    // 如果服务端返回 502/503，尝试一次简单重试以应对短暂网关问题
    if (!response.ok && (response.status === 502 || response.status === 503) && !retry502) {
      retry502 = true
      console.warn('[Metric API] 收到后端网关错误 (502/503)，尝试重试一次')
      response = await fetch(fullUrl, config)
      console.log('[Metric API] 重试后响应状态:', response.status)
      console.log('[Metric API] 重试后响应OK:', response.ok)
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败', code: response.status }))
      console.error('[Metric API] 请求失败，详细信息:', {
        status: response.status,
        url: fullUrl,
        error: error,
        requestBody: options.body
      })
      // 401 时尝试自动刷新 Token
      if (response.status === 401 && token && !retryMetric) {
        try {
          const refreshResp = await fetch(`${API_BASE_URL}/refresh`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${token}`,
            },
          })
          if (refreshResp.ok) {
            const refreshData = await refreshResp.json()
            const newToken = refreshData?.token
            if (newToken) {
              localStorage.setItem('token', newToken)
              // 更新请求头中的 token，然后重试
              config.headers = {
                ...config.headers,
                Authorization: `Bearer ${newToken}`,
              }
              retryMetric = true
              response = await fetch(fullUrl, config)
              if (!response.ok) {
                const err2 = await response.json().catch(() => ({ message: '请求失败', code: response.status }))
                console.error('[Metric API] 重试请求失败，详细信息:', {
                  status: response.status,
                  url: fullUrl,
                  error: err2
                })
                throw new Error(err2.message || `请求失败 (${response.status})`)
              }
            }
          } else {
            // 刷新 Token 失败，说明当前 Token 已完全无效，跳转到登录页
            console.error('[Metric API] 刷新 Token 失败，跳转到登录页')
            // 清除认证信息
            localStorage.removeItem('token')
            localStorage.removeItem('currentUser')
            // 跳转到登录页
            window.location.href = '/login'
            throw new Error('登录已过期，请重新登录')
          }
        } catch (e) {
          // 刷新 Token 时发生错误，说明当前 Token 已完全无效，跳转到登录页
          console.error('[Metric API] 刷新 Token 时发生错误，跳转到登录页:', e)
          // 清除认证信息
          localStorage.removeItem('token')
          localStorage.removeItem('currentUser')
          // 跳转到登录页
          window.location.href = '/login'
          throw new Error('登录已过期，请重新登录')
        }
      }
      // 构建详细的错误信息
      const errorMessage = error.message || error.error || error.detail || `请求失败 (${response.status})`
      const detailedError = `${errorMessage} (状态码: ${response.status})`
      console.error('[Metric API] 详细错误信息:', detailedError)
      throw new Error(detailedError)
    }

    // 处理空响应体的情况
    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      // 如果响应体不是JSON，返回空数组
      console.log('[Metric API] 响应体不是JSON，返回空数组')
      return []
    }

    // 尝试解析响应体
    try {
      // 先检查响应体是否为空
      const text = await response.text()
      if (!text) {
        // 如果响应体为空，返回空数组
        console.log('[Metric API] 响应体为空，返回空数组')
        return []
      }
      // 如果响应体不为空，尝试解析它
      const data = JSON.parse(text)
      console.log('[Metric API] 响应体解析成功:', data)
      return data
    } catch (e) {
      // 如果解析失败，返回空数组
      console.log('[Metric API] 响应体解析失败，返回空数组:', e)
      return []
    }
  } catch (error) {
    console.error('Metric API请求异常:', error)
    throw error
  }
}

export const patientApi = {
  // 获取患者列表（分页）
  getPatients: (params = {}) => {
    const query = new URLSearchParams(params).toString()
    return patientRequest(`/patients${query ? `?${query}` : ''}`, {
      method: 'GET',
    })
  },

  // 搜索患者
  searchPatients: (params) => {
    const query = new URLSearchParams(params).toString()
    return patientRequest(`/patients/search?${query}`, {
      method: 'GET',
    })
  },

  // 获取患者详情
  getPatient: (id) => patientRequest(`/patients/${id}`, {
    method: 'GET',
  }),

  // 获取患者详情（别名）
  getPatientById: (id) => patientRequest(`/patients/${id}`, {
    method: 'GET',
  }),

  // 创建患者
  createPatient: (data) => patientRequest('/patients', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 更新患者信息
  updatePatient: (id, data) => patientRequest(`/patients/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

  // 删除患者
  deletePatient: (id) => patientRequest(`/patients/${id}`, {
    method: 'DELETE',
  }),

  // 批量删除患者
  batchDeletePatients: (ids) => patientRequest('/patients/batch', {
    method: 'DELETE',
    body: JSON.stringify(ids),
  }),

  // 软删除患者
  softDeletePatient: (id) => patientRequest(`/patients/${id}/soft`, {
    method: 'DELETE',
  }),

  // 恢复患者
  restorePatient: (id) => patientRequest(`/patients/${id}/restore`, {
    method: 'PUT',
  }),

  // 更新患者状态
  updateStatus: (id, status) => patientRequest(`/patients/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  }),

  // 批量更新状态
  batchUpdateStatus: (ids, status) => patientRequest('/patients/batch/status', {
    method: 'PUT',
    body: JSON.stringify({ patientIds: ids, status }),
  }),

  // 分配医生
  assignDoctor: (patientId, doctorId) => patientRequest(`/patients/${patientId}/doctor`, {
    method: 'PUT',
    body: JSON.stringify({ doctorId }),
  }),

  // 获取患者统计信息
  getStatistics: (hospitalId) => {
    const query = hospitalId ? `?hospitalId=${hospitalId}` : ''
    return patientRequest(`/patients/statistics${query}`, {
      method: 'GET',
    })
  },
}

export const metricApi = {
  // ==================== 医学影像 API ====================
  // 获取所有医学影像
  getAllMedicalImages: () => metricRequest('/metric/images', {
    method: 'GET',
  }),

  // 创建医学影像
  createMedicalImage: (data) => metricRequest('/metric/images', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 根据患者姓名查询医学影像
  getMedicalImagesByPatientName: (patientName) => metricRequest(`/metric/images/patient/${encodeURIComponent(patientName)}`, {
    method: 'GET',
  }),

  // 删除医学影像
  deleteMedicalImage: (id) => metricRequest(`/metric/images/${id}`, {
    method: 'DELETE',
  }),

  // ==================== 分析结果 API ====================
  // 获取所有分析结果
  getAllAnalysisResults: () => metricRequest('/metric/analyses', {
    method: 'GET',
  }),

  // 创建分析结果
  createAnalysisResult: (data) => metricRequest('/metric/analyses', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 根据患者姓名查询分析结果
  getAnalysisResultsByPatientName: (patientName) => metricRequest(`/metric/analyses/patient/${encodeURIComponent(patientName)}`, {
    method: 'GET',
  }),

  // ==================== 报表 API ====================
  // 获取所有报表
  getAllReports: () => metricRequest('/metric/reports', {
    method: 'GET',
  }),

  // 创建报表
  createReport: (data) => metricRequest('/metric/reports', {
    method: 'POST',
    body: JSON.stringify(data),
  }),

  // 根据患者姓名查询报表
  getReportsByPatientName: (patientName) => metricRequest(`/metric/reports/patient/${encodeURIComponent(patientName)}`, {
    method: 'GET',
  }),

  // ==================== AI Agent API ====================
  // 医疗影像分析与报表生成
  analyzeAndReport: (userInput) => metricRequest('/metric/ai/analyze-and-report', {
    method: 'POST',
    body: userInput,
  }),
}
