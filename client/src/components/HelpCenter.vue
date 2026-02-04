<template>
  <div class="help-center">
    <div class="page-header">
      <h2>帮助中心</h2>
    </div>

    <div class="content-placeholder">
      <div class="placeholder-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"></circle>
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
          <line x1="12" y1="17" x2="12.01" y2="17"></line>
        </svg>
      </div>
      <h3>帮助中心模块</h3>
      <p>此功能正在开发中...</p>
    </div>
  </div>
</template>

<style scoped>
.help-center {
  min-height: 60vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.page-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.content-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.placeholder-icon {
  width: 80px;
  height: 80px;
  margin-bottom: 1.5rem;
  color: #d9d9d9;
}

.placeholder-icon svg {
  width: 100%;
  height: 100%;
}

.content-placeholder h3 {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 0.5rem;
}

.content-placeholder p {
  color: #999;
  font-size: 0.95rem;
  margin: 0;
}
</style>
