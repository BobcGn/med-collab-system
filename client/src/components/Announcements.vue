<template>
  <div class="announcements">
    <div class="page-header">
      <h2>系统公告</h2>
    </div>

    <div class="content-placeholder">
      <div class="placeholder-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
        </svg>
      </div>
      <h3>系统公告模块</h3>
      <p>此功能正在开发中...</p>
    </div>
  </div>
</template>

<style scoped>
.announcements {
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
