<template>
  <div class="notifications">
    <div class="page-header">
      <h2>消息通知</h2>
    </div>

    <div class="content-placeholder">
      <div class="placeholder-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
        </svg>
      </div>
      <h3>消息通知模块</h3>
      <p>此功能正在开发中...</p>
    </div>
  </div>
</template>

<style scoped>
.notifications {
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
