<template>
  <div class="analysis-result-manage">
    <MetricPatientList 
      title="分析结果管理" 
      source="analysis" 
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
  // 跳转到分析结果浏览页面
  router.push({
    path: '/metric/analyses/detail',
    query: { patientId: patient.id, patientName: patient.name }
  })
}
</script>

<style scoped>
.analysis-result-manage {
  min-height: 100vh;
  background-color: #f5f5f5;
}
</style>