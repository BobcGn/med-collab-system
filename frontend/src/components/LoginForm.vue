<template>
  <div class="login-container">
    <div class="login-card">
      <h2>登录</h2>
      <form @submit.prevent="handleLogin">
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

        <!-- 登录表单 -->
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="formData.username"
            type="text"
            :placeholder="formData.role === 'admin' ? '请输入管理员账号（admin-用户id）' : '请输入用户账号（医院id-部门id-用户id）'"
            required
          />
        </div>
        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            required
          />
        </div>

        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div class="register-link">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { authApi } from '../utils/api'
import { authStore } from '../utils/auth'
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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 2rem;
}

.login-card {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  width: 100%;
  max-width: 400px;
}

.login-card h2 {
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

.form-group input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
}

.form-group input:focus {
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
  margin-top: 0.5rem;
}

button:hover:not(:disabled) {
  opacity: 0.9;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.register-link {
  margin-top: 1.5rem;
  text-align: center;
  color: #666;
  font-size: 0.9rem;
}

.register-link a {
  color: #667eea;
  text-decoration: none;
  transition: color 0.2s;
}

.register-link a:hover {
  text-decoration: underline;
  color: #764ba2;
}
</style>
