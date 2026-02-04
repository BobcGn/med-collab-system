<template>
  <div class="register-container">
    <div class="register-wrapper">
      <div class="register-card">
        <div class="register-header">
          <h2>医工协同创新平台</h2>
          <p class="register-subtitle">请注册您的账号</p>
        </div>
        <form @submit.prevent="handleRegister">
          <!-- 身份选择 -->
          <div class="form-group">
            <label class="form-label">身份选择</label>
            <div class="role-selector">
              <label class="role-option">
                <input
                  type="radio"
                  v-model="formData.role"
                  value="admin"
                  required
                />
                <span class="role-text">管理员</span>
              </label>
              <label class="role-option">
                <input
                  type="radio"
                  v-model="formData.role"
                  value="user"
                  required
                />
                <span class="role-text">普通用户</span>
              </label>
            </div>
          </div>

          <!-- 管理员注册表单 -->
          <div v-if="formData.role === 'admin'" class="admin-form">
            <div class="form-group">
              <label for="admin-fullName" class="form-label">真实姓名</label>
              <input
                id="admin-fullName"
                v-model="formData.fullName"
                type="text"
                placeholder="请输入真实姓名"
                required
                class="form-input"
              />
            </div>
          </div>

          <!-- 用户注册表单 -->
          <div v-else-if="formData.role === 'user'" class="user-form">
            <div class="form-group">
              <label for="hospitalId" class="form-label">所属医院</label>
              <select
                id="hospitalId"
                v-model="formData.hospitalId"
                required
                @change="handleHospitalChange"
                class="form-input"
              >
                <option value="">请选择医院</option>
                <option v-for="hospital in hospitals" :key="hospital.id" :value="hospital.id">
                  {{ hospital.name }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label for="deptCode" class="form-label">所属部门</label>
              <select
                id="deptCode"
                v-model="formData.deptCode"
                required
                class="form-input"
              >
                <option value="">请选择部门</option>
                <option v-for="dept in departments" :key="dept.id" :value="dept.id">
                  {{ dept.name }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label for="user-fullName" class="form-label">真实姓名</label>
              <input
                id="user-fullName"
                v-model="formData.fullName"
                type="text"
                placeholder="请输入真实姓名"
                required
                class="form-input"
              />
            </div>
          </div>

          <!-- 通用密码表单 -->
          <div class="form-group">
            <label for="password" class="form-label">密码</label>
            <input
              id="password"
              v-model="formData.password"
              type="password"
              placeholder="请输入密码（至少8位，包含大小写字母和数字）"
              required
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label for="confirmPassword" class="form-label">确认密码</label>
            <input
              id="confirmPassword"
              v-model="confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              required
              class="form-input"
            />
          </div>

          <div v-if="errorMessage" class="error-message">
            {{ errorMessage }}
          </div>
          <div v-if="successMessage" class="success-message">
            {{ successMessage }}
          </div>
          <button type="submit" :disabled="loading" class="register-button">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>
        <div class="login-link">
          已有账号？<router-link to="/login" class="login-link-text">立即登录</router-link>
        </div>
      </div>
    </div>

    <!-- 注册成功弹窗 -->
    <div v-if="registerSuccess" class="modal-overlay" @click.self="registerSuccess = false">
      <div class="modal-content">
        <div class="modal-header">
          <div class="success-icon">✓</div>
          <h3>注册成功</h3>
        </div>
        <div class="modal-body">
          <p class="modal-title">您的账号为：</p>
          <div class="username-box" @click="copyUsername" title="点击复制">
            <code class="username-text">{{ registeredUsername }}</code>
            <button class="copy-button" @click.stop="copyUsername" title="复制账号">
              <span v-if="!copied">复制</span>
              <span v-else>已复制</span>
            </button>
          </div>
          <p class="modal-tip">点击账号或复制按钮即可复制，然后使用此账号登录</p>
        </div>
        <div class="modal-footer">
          <button class="modal-button" @click="goToLogin">去登录</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../utils/api.js'
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
const registerSuccess = ref(false)
const registeredUsername = ref('')
const copied = ref(false)

// 复制用户名
const copyUsername = () => {
  navigator.clipboard.writeText(registeredUsername.value).then(() => {
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }).catch(err => {
    console.error('复制失败:', err)
  })
}

// 跳转到登录页
const goToLogin = () => {
  registerSuccess.value = false
  router.push('/login')
}

// 加载医院列表
const loadHospitals = async () => {
  try {
    const result = await authApi.getAllHospitals()
    hospitals.value = Array.isArray(result) ? result : []
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
    const result = await authApi.getHospitalDepartments(hospitalId)
    departments.value = Array.isArray(result) ? result : []
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
    const response = await authApi.register({
      role: formData.value.role === 'admin' ? 'admin' : 'doctor',
      hospitalId: formData.value.hospitalId,
      deptCode: formData.value.deptCode,
      fullName: formData.value.fullName,
      password: formData.value.password,
    })

    // 注册成功，保存用户名并显示弹窗
    registeredUsername.value = response.user.username
    registerSuccess.value = true
  } catch (error) {
    errorMessage.value = error.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 注册页面容器 - 全屏覆盖 */
.register-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  width: 100vw;
  max-width: none !important;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 2rem;
  margin: 0 !important;
  box-sizing: border-box;
  z-index: 1000;
}

.register-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.register-card {
  background: white;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  padding: 3.5rem;
  width: 100%;
  max-width: 520px;
  min-width: 400px;
  max-height: 90vh;
  overflow-y: auto;
  text-align: center;
  animation: fadeInUp 0.5s ease-out;
  margin: 0 auto;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 自定义滚动条样式 */
.register-card::-webkit-scrollbar {
  width: 8px;
}

.register-card::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 10px;
}

.register-card::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 10px;
}

.register-card::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.register-header {
  margin-bottom: 2.5rem;
}

.register-header h2 {
  font-size: 2rem;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 0.75rem;
  letter-spacing: -0.5px;
}

.register-subtitle {
  font-size: 1rem;
  color: #666;
  font-weight: 400;
}

/* 表单组 */
.form-group {
  margin-bottom: 1.5rem;
  text-align: left;
}

.form-label {
  display: block;
  margin-bottom: 0.625rem;
  color: #333;
  font-weight: 600;
  font-size: 0.95rem;
  letter-spacing: 0.2px;
}

/* 表单输入框 */
.form-input {
  width: 100%;
  padding: 0.875rem 1.125rem;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  font-size: 1rem;
  box-sizing: border-box;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: #fafafa;
  color: #333;
}

.form-input:hover {
  border-color: #d1d5db;
  background: #ffffff;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
  background: white;
}

.form-input::placeholder {
  color: #9ca3af;
}

/* 下拉选择框样式 */
select.form-input {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23667eea' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 1rem center;
  padding-right: 2.5rem;
}

/* 身份选择器样式 */
.role-selector {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: #f5f5f5;
  border-radius: 12px;
  border: 2px solid transparent;
  transition: all 0.3s;
}

.role-selector:hover {
  background: #f0f0f0;
}

.role-option {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  cursor: pointer;
  font-size: 0.95rem;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex: 1;
  justify-content: center;
  position: relative;
}

.role-option:hover {
  background: rgba(102, 126, 234, 0.08);
}

.role-option input[type="radio"] {
  width: 18px;
  height: 18px;
  margin: 0;
  accent-color: #667eea;
  cursor: pointer;
}

.role-option input[type="radio"]:checked + .role-text {
  color: #667eea;
  font-weight: 600;
}

.role-text {
  color: #555;
  font-weight: 500;
  transition: all 0.3s;
  user-select: none;
}

/* 错误信息 */
.error-message {
  color: #dc2626;
  margin-bottom: 1.5rem;
  text-align: center;
  font-size: 0.9rem;
  background: #fef2f2;
  padding: 0.875rem 1rem;
  border-radius: 8px;
  border-left: 4px solid #dc2626;
  animation: shake 0.4s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}

/* 成功信息 */
.success-message {
  color: #16a34a;
  margin-bottom: 1.5rem;
  text-align: center;
  font-size: 0.9rem;
  background: #f0fdf4;
  padding: 0.875rem 1rem;
  border-radius: 8px;
  border-left: 4px solid #16a34a;
  animation: fadeIn 0.3s ease-in-out;
}

/* 注册按钮 */
.register-button {
  width: 100%;
  padding: 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-top: 0.5rem;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.35);
  letter-spacing: 0.3px;
}

.register-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.45);
}

.register-button:active:not(:disabled) {
  transform: translateY(0);
}

.register-button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.2);
}

/* 登录链接 */
.login-link {
  margin-top: 2rem;
  text-align: center;
  color: #666;
  font-size: 0.95rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e5e7eb;
}

.login-link-text {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
  transition: all 0.3s;
  position: relative;
  margin-left: 0.25rem;
}

.login-link-text:hover {
  color: #764ba2;
}

.login-link-text::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 2px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.3s;
}

.login-link-text:hover::after {
  width: 100%;
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

/* 响应式设计 */
@media (max-width: 768px) {
  .register-container {
    padding: 1.5rem;
  }

  .register-wrapper {
    max-width: 100%;
  }

  .register-card {
    padding: 2.5rem;
    max-width: 100%;
    min-width: unset;
    width: 100%;
    border-radius: 16px;
  }

  .register-header h2 {
    font-size: 1.75rem;
  }

  .role-selector {
    flex-direction: column;
    gap: 0.5rem;
    padding: 1rem;
  }

  .role-option {
    flex-direction: row;
    justify-content: flex-start;
    padding: 0.875rem 1rem;
  }
}

@media (max-width: 480px) {
  .register-container {
    padding: 1rem;
  }

  .register-card {
    padding: 2rem;
    border-radius: 12px;
    min-width: unset;
  }

  .register-header {
    margin-bottom: 2rem;
  }

  .register-header h2 {
    font-size: 1.5rem;
  }

  .register-subtitle {
    font-size: 0.9rem;
  }

  .form-group {
    margin-bottom: 1.25rem;
  }

  .form-input {
    padding: 0.75rem 1rem;
    font-size: 0.95rem;
  }

  .register-button {
    padding: 0.875rem;
    font-size: 0.95rem;
  }

  .login-link {
    margin-top: 1.5rem;
    font-size: 0.9rem;
  }
}

/* 注册成功弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
  animation: fadeIn 0.3s ease-in-out;
}

.modal-content {
  background: white;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  padding: 2.5rem;
  max-width: 480px;
  width: 90%;
  text-align: center;
  animation: slideUp 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  margin-bottom: 2rem;
}

.success-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 1.5rem;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 3rem;
  color: white;
  font-weight: bold;
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.35);
  animation: scaleIn 0.5s ease-in-out;
}

@keyframes scaleIn {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.modal-header h3 {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0;
  letter-spacing: -0.5px;
}

.modal-body {
  margin-bottom: 2rem;
}

.modal-title {
  font-size: 1.1rem;
  color: #666;
  margin-bottom: 1.5rem;
  font-weight: 500;
}

.username-box {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 1.25rem;
  margin-bottom: 1rem;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  position: relative;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.35);
}

.username-box:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.45);
}

.username-text {
  color: white;
  font-size: 1.25rem;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  letter-spacing: 0.5px;
  word-break: break-all;
  flex: 1;
  background: transparent;
  padding: 0;
}

.copy-button {
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 0.625rem 1.25rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  transition: all 0.3s;
  white-space: nowrap;
}

.copy-button:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  transform: scale(1.05);
}

.copy-button:active {
  transform: scale(0.98);
}

.modal-tip {
  color: #999;
  font-size: 0.9rem;
  margin: 0;
}

.modal-footer {
  border-top: 1px solid #e5e7eb;
  padding-top: 1.5rem;
}

.modal-button {
  width: 100%;
  padding: 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.35);
  letter-spacing: 0.3px;
}

.modal-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.45);
}

.modal-button:active {
  transform: translateY(0);
}

/* 弹窗响应式 */
@media (max-width: 480px) {
  .modal-content {
    padding: 2rem;
    max-width: 95%;
  }

  .success-icon {
    width: 60px;
    height: 60px;
    font-size: 2.25rem;
  }

  .modal-header h3 {
    font-size: 1.5rem;
  }

  .username-text {
    font-size: 1.1rem;
  }

  .copy-button {
    padding: 0.5rem 1rem;
    font-size: 0.85rem;
  }
}
</style>
