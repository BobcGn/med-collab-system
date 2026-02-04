<template>
  <div class="login-container">
    <div class="login-wrapper">
      <div class="login-card">
        <div class="login-header">
          <h2>医工协同创新平台</h2>
          <p class="login-subtitle">请登录您的账号</p>
        </div>
        <form @submit.prevent="handleLogin">
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

          <!-- 登录表单 -->
          <div class="form-group">
            <label for="username" class="form-label">用户名</label>
            <input
              id="username"
              v-model="formData.username"
              type="text"
              :placeholder="formData.role === 'admin' ? '请输入管理员账号（admin-用户id）' : '请输入用户账号（医院id-部门id-用户id）'"
              required
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label for="password" class="form-label">密码</label>
            <input
              id="password"
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              required
              class="form-input"
            />
          </div>

          <div v-if="errorMessage" class="error-message">
            {{ errorMessage }}
          </div>
          <button type="submit" :disabled="loading" class="login-button">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
        <div class="register-link">
          还没有账号？<router-link to="/register" class="register-link-text">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { authApi } from '../utils/api.js'
import { authStore } from '../utils/auth.js'
import { useRouter } from 'vue-router'

const router = useRouter()

const formData = ref({
  role: 'user',
  username: '',
  password: '',
})

const loading = ref(false)
const errorMessage = ref('')

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await authApi.login({
      username: formData.value.username,
      password: formData.value.password,
    })

    // 保存token和用户信息
    authStore.setToken(result.token)
    authStore.setCurrentUser(result.user)

    // 跳转到首页
    router.push('/')
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 登录页面容器 - 全屏覆盖 */
.login-container {
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

/* 登录包装器 */
.login-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

/* 登录卡片 */
.login-card {
  background: white;
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  padding: 3.5rem;
  width: 100%;
  max-width: 520px;
  min-width: 400px;
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

/* 登录头部 */
.login-header {
  margin-bottom: 2.5rem;
}

.login-header h2 {
  font-size: 2rem;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 0.75rem;
  letter-spacing: -0.5px;
}

.login-subtitle {
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

/* 登录按钮 */
.login-button {
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

.login-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.45);
}

.login-button:active:not(:disabled) {
  transform: translateY(0);
}

.login-button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.2);
}

/* 注册链接 */
.register-link {
  margin-top: 2rem;
  text-align: center;
  color: #666;
  font-size: 0.95rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e5e7eb;
}

.register-link-text {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
  transition: all 0.3s;
  position: relative;
  margin-left: 0.25rem;
}

.register-link-text:hover {
  color: #764ba2;
}

.register-link-text::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 2px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.3s;
}

.register-link-text:hover::after {
  width: 100%;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-container {
    padding: 1.5rem;
  }

  .login-wrapper {
    max-width: 100%;
  }

  .login-card {
    padding: 2.5rem;
    max-width: 100%;
    min-width: unset;
    width: 100%;
    border-radius: 16px;
  }

  .login-header h2 {
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
  .login-container {
    padding: 1rem;
  }

  .login-card {
    padding: 2rem;
    border-radius: 12px;
    min-width: unset;
  }

  .login-header {
    margin-bottom: 2rem;
  }

  .login-header h2 {
    font-size: 1.5rem;
  }

  .login-subtitle {
    font-size: 0.9rem;
  }

  .form-group {
    margin-bottom: 1.25rem;
  }

  .form-input {
    padding: 0.75rem 1rem;
    font-size: 0.95rem;
  }

  .login-button {
    padding: 0.875rem;
    font-size: 0.95rem;
  }

  .register-link {
    margin-top: 1.5rem;
    font-size: 0.9rem;
  }
}
</style>
