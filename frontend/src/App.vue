<script setup>
import { authStore } from './utils/auth'
</script>

<template>
  <div id="app">
    <div class="app-container">
      <header v-if="authStore.isAuthenticated()" class="app-header">
        <div class="header-left">
          <h1>医工协同创新平台</h1>
        </div>
        <nav class="header-nav">
          <router-link to="/profile">个人中心</router-link>
          <router-link to="/users">用户列表</router-link>
          <router-link v-if="authStore.hasRole('admin')" to="/manage/users">用户管理</router-link>
          <router-link v-if="authStore.hasRole('admin')" to="/manage/hospitals">医院管理</router-link>
        </nav>
        <div class="header-right">
          <span>欢迎：{{ authStore.getCurrentUser()?.fullName || '用户' }}</span>
          <button @click="handleLogout">退出</button>
        </div>
      </header>
      <main class="app-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
export default {
  methods: {
    handleLogout() {
      authStore.clearAuth()
      this.$router.push('/login')
    },
  },
}
</script>

<style>
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: #f5f5f5;
  color: #333;
}

#app {
  min-height: 100vh;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #ffffff;
  padding: 0.75rem 2.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left h1 {
  font-size: 1.4rem;
  font-weight: 600;
  color: #4c6fff;
  letter-spacing: 0.08em;
}

.header-nav {
  display: flex;
  gap: 1.75rem;
}

.header-nav a {
  color: #666;
  text-decoration: none;
  font-weight: 500;
  font-size: 0.95rem;
  padding-bottom: 0.25rem;
  border-bottom: 2px solid transparent;
  transition:
    color 0.2s,
    border-color 0.2s;
}

.header-nav a:hover,
.header-nav a.router-link-active {
  color: #4c6fff;
  border-color: #4c6fff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.9rem;
  color: #555;
}

.header-right button {
  padding: 0.4rem 1rem;
  background: #f3f4ff;
  color: #4c6fff;
  border: 1px solid #d6dcff;
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  transition:
    background 0.2s,
    color 0.2s,
    box-shadow 0.2s;
}

.header-right button:hover {
  background: #4c6fff;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(76, 111, 255, 0.32);
}

.app-main {
  flex: 1;
  padding: 1.5rem 2.5rem 2.5rem;
}
</style>
