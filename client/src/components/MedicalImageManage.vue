<template>
  <div class="medical-image-manage">
    <MetricPatientList 
      title="医学影像管理" 
      source="image" 
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
  // 跳转到医学影像AI聊天页面
  router.push({
    path: '/metric/images/chat',
    query: { patientId: patient.id, patientName: patient.name }
  })
}
</script>

<style scoped>
.medical-image-manage {
  min-height: 100vh;
  background-color: #f5f5f5;
}
</style>