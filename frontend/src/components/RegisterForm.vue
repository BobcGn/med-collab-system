<template>
  <div class="register-container">
    <div class="register-card">
      <h2>注册</h2>
      <form @submit.prevent="handleRegister">
        <!-- 身份选择 -->
        <div class="form-group">
          <label>身份选择</label>
          <div class="role-selector">
            <label class="role-option">
              <input
                type="radio"
                v-model="formData.role"
                value="admin"
                required
              />
              <span>管理员</span>
            </label>
            <label class="role-option">
              <input
                type="radio"
                v-model="formData.role"
                value="user"
                required
              />
              <span>普通用户</span>
            </label>
          </div>
        </div>

        <!-- 管理员注册表单 -->
        <div v-if="formData.role === 'admin'" class="admin-form">
          <div class="form-group">
            <label for="admin-fullName">真实姓名</label>
            <input
              id="admin-fullName"
              v-model="formData.fullName"
              type="text"
              placeholder="请输入真实姓名"
              required
            />
          </div>
        </div>

        <!-- 用户注册表单 -->
        <div v-else-if="formData.role === 'user'" class="user-form">
          <div class="form-group">
            <label for="hospitalId">所属医院</label>
            <select
              id="hospitalId"
              v-model="formData.hospitalId"
              required
              @change="handleHospitalChange"
            >
              <option value="">请选择医院</option>
              <option v-for="hospital in hospitals" :key="hospital.id" :value="hospital.id">
                {{ hospital.name }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label for="deptCode">所属部门</label>
            <select
              id="deptCode"
              v-model="formData.deptCode"
              required
            >
              <option value="">请选择部门</option>
              <option v-for="dept in departments" :key="dept.id" :value="dept.id">
                {{ dept.name }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label for="user-fullName">真实姓名</label>
            <input
              id="user-fullName"
              v-model="formData.fullName"
              type="text"
              placeholder="请输入真实姓名"
              required
            />
          </div>
        </div>

        <!-- 通用密码表单 -->
        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="formData.password"
            type="password"
            placeholder="请输入密码（至少8位，包含大小写字母和数字）"
            required
          />
        </div>
        <div class="form-group">
          <label for="confirmPassword">确认密码</label>
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            required
          />
        </div>

        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        <div v-if="successMessage" class="success-message">
          {{ successMessage }}
        </div>

        <button type="submit" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <div class="login-link">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../utils/api'
import { useRouter } from 'vue-router'

const router = useRouter()

const formData = ref({
  role: '',
  hospitalId: '',
  deptCode: '',
  fullName: '',
  password: '',
})

const confirmPassword = ref('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const hospitals = ref([])
const departments = ref([])

// 加载医院列表
const loadHospitals = async () => {
  try {
    // 这里需要调用获取医院列表的API
    // 暂时使用模拟数据
    hospitals.value = [
      { id: 'H001', name: '北京协和医院' },
      { id: 'H002', name: '上海瑞金医院' },
      { id: 'H003', name: '广州中山大学附属医院' },
    ]
  } catch (error) {
    console.error('加载医院列表失败:', error)
  }
}

// 加载部门列表
const loadDepartments = async (hospitalId) => {
  if (!hospitalId) {
    departments.value = []
    return
  }
  
  try {
    // 这里需要调用获取部门列表的API
    // 暂时使用模拟数据
    const deptData = {
      'H001': [
        { id: 'D001', name: '内科' },
        { id: 'D002', name: '外科' },
        { id: 'D003', name: '儿科' },
      ],
      'H002': [
        { id: 'D001', name: '内科' },
        { id: 'D002', name: '外科' },
        { id: 'D004', name: '妇产科' },
      ],
      'H003': [
        { id: 'D001', name: '内科' },
        { id: 'D002', name: '外科' },
        { id: 'D005', name: '神经内科' },
      ],
    }
    departments.value = deptData[hospitalId] || []
  } catch (error) {
    console.error('加载部门列表失败:', error)
  }
}

// 医院选择变化处理
const handleHospitalChange = () => {
  formData.value.deptCode = ''
  loadDepartments(formData.value.hospitalId)
}

// 页面加载时获取医院列表
onMounted(() => {
  loadHospitals()
})

const handleRegister = async () => {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    // 验证密码
    if (formData.value.password !== confirmPassword.value) {
      errorMessage.value = '两次输入的密码不一致'
      return
    }

    // 密码强度检查
    const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/
    if (!passwordRegex.test(formData.value.password)) {
      errorMessage.value = '密码强度不足，请输入至少8位，包含大小写字母和数字的密码'
      return
    }

    // 调用注册API
    await authApi.register({
      role: formData.value.role === 'admin' ? 'admin' : 'doctor',
      hospitalId: formData.value.hospitalId,
      deptCode: formData.value.deptCode,
      fullName: formData.value.fullName,
      password: formData.value.password,
    })

    successMessage.value = '注册成功！正在跳转到登录页面...'

    // 2秒后跳转到登录页面
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error) {
    errorMessage.value = error.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 2rem;
}

.register-card {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 450px;
  max-height: 90vh;
  overflow-y: auto;
}

.register-card h2 {
  text-align: center;
  margin-bottom: 2rem;
  color: #333;
}

.form-group {
  margin-bottom: 1.2rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

/* 身份选择器样式 */
.role-selector {
  display: flex;
  gap: 1.5rem;
  margin-top: 0.5rem;
}

.role-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-size: 0.95rem;
}

.role-option input[type="radio"] {
  width: auto;
  margin: 0;
}

.role-option span {
  color: #555;
}

.error-message {
  color: #e74c3c;
  margin-bottom: 1rem;
  text-align: center;
  font-size: 0.9rem;
}

.success-message {
  color: #27ae60;
  margin-bottom: 1rem;
  text-align: center;
  font-size: 0.9rem;
}

button {
  width: 100%;
  padding: 0.75rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
  margin-top: 1rem;
}

button:hover:not(:disabled) {
  opacity: 0.9;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-link {
  margin-top: 1.5rem;
  text-align: center;
  color: #666;
  font-size: 0.9rem;
}

.login-link a {
  color: #667eea;
  text-decoration: none;
  transition: color 0.2s;
}

.login-link a:hover {
  text-decoration: underline;
  color: #764ba2;
}

/* 表单切换动画 */
.admin-form,
.user-form {
  animation: fadeIn 0.3s ease-in-out;
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
</style>
