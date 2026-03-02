<template>
  <div class="metric-report-manage">
    <MetricPatientList 
      title="最终报表管理" 
      source="report" 
      @patientClick="handlePatientClick" 
    />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import MetricPatientList from './MetricPatientList.vue'

const router = useRouter()

/**
 * 处理患者点击事件
 * @param {Object} patient - 患者对象
 */
const handlePatientClick = (patient) => {
  // 跳转到报表列表页面
  router.push({
    path: '/metric/reports/list',
    query: { patientId: patient.id, patientName: patient.name }
  })
}
</script>

<style scoped>
.metric-report-manage {
  min-height: 100vh;
  background-color: #f5f5f5;
}
</style>