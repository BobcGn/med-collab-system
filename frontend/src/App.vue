<script setup>
import { authStore } from './utils/auth'
</script>

<template>
  <div id="app">
    <div class="app-container">
      <header v-if="authStore.isAuthenticated()" class="app-header">
        <div class="header-left">
          <h1>医疗协作系统</h1>
        </div>
        <nav class="header-nav">
          <router-link to="/profile">个人中心</router-link>
          <router-link to="/users">用户列表</router-link>
          <router-link v-if="authStore.hasRole('admin')" to="/manage/users">用户管理</router-link>
        </nav>
        <div class="header-right">
          <span>{{ authStore.getCurrentUser()?.fullName }}</span>
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
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

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
  background: white;
  padding: 1rem 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left h1 {
  font-size: 1.5rem;
  color: #667eea;
}

.header-nav {
  display: flex;
  gap: 2rem;
}

.header-nav a {
  color: #666;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.header-nav a:hover,
.header-nav a.router-link-active {
  color: #667eea;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header-right span {
  color: #666;
}

.header-right button {
  padding: 0.5rem 1rem;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: background 0.2s;
}

.header-right button:hover {
  background: #5568d3;
}

.app-main {
  flex: 1;
  padding: 2rem;
}
</style>
