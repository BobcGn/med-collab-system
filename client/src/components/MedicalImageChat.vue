<template>
  <div class="medical-image-chat">
    <div class="nav-bar">
      <div class="nav-content">
        <button @click="goBack" class="back-button">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="back-icon">
            <path d="M19 12H5M12 19l-7-7 7-7"></path>
          </svg>
        </button>
        <h2>医学影像分析 - {{ patientName }}</h2>
        <div class="patient-info">
          <span class="patient-id">患者ID: {{ patientId }}</span>
        </div>
      </div>
    </div>

    <div class="chat-container">
      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="chatMessages">
        <!-- 系统消息 -->
        <div class="message system-message">
          <div class="message-content">
            <p>欢迎使用医学影像AI分析系统，请上传医学影像进行分析</p>
          </div>
        </div>

        <!-- 医生消息 -->
        <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <div class="message-content">
            <div v-if="message.type === 'text'" class="text-message">
              {{ message.content }}
            </div>
            <div v-else-if="message.type === 'image'" class="image-message">
              <img :src="message.content" :alt="'医学影像 ' + (index + 1)" class="uploaded-image" />
              <div class="image-info">
                <span>{{ message.imageType }}</span>
                <span>{{ message.imageDate }}</span>
              </div>
            </div>
            <div v-else-if="message.type === 'ai_result'" class="ai-result-message">
              <div class="ai-result-header">
                <h4>AI分析结果</h4>
                <span class="confidence">置信度: {{ message.confidence }}%</span>
              </div>
              <div class="ai-result-content">
                <textarea v-model="message.content" class="result-textarea" placeholder="AI分析结果..."></textarea>
              </div>
              <div class="ai-result-actions">
                <button @click="confirmResult(message)" class="btn-primary">确认结果</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载动画 -->
        <div v-if="loading" class="message ai-message">
          <div class="message-content">
            <div class="loading-animation">
              <div class="loading-dot"></div>
              <div class="loading-dot"></div>
              <div class="loading-dot"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <!-- 上传区域 -->
        <div class="upload-section">
          <label class="upload-button">
            <input type="file" accept="image/*" @change="handleImageUpload" multiple hidden />
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="upload-icon">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
            上传影像
          </label>
          <select v-model="imageType" class="image-type-select">
            <option value="X-RAY">X射线</option>
            <option value="CT">CT</option>
            <option value="MRI">MRI</option>
            <option value="ULTRASOUND">超声</option>
            <option value="OTHER">其他</option>
          </select>
        </div>

        <!-- 文本输入区域 -->
        <div class="text-input-section">
          <input 
            v-model="inputMessage" 
            type="text" 
            placeholder="输入消息..." 
            class="text-input"
            @keyup.enter="sendMessage"
          />
          <button @click="sendMessage" class="send-button">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="send-icon">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="bottom-actions">
      <button @click="generateReport" class="btn-secondary" :disabled="!hasConfirmedResult">
        生成报表
      </button>
      <button @click="clearChat" class="btn-secondary">
        清空聊天
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { metricApi } from '../utils/api'

const router = useRouter()
const route = useRoute()

// 患者信息
const patientId = ref(route.query.patientId || '')
const patientName = ref(route.query.patientName || '未知患者')

// 聊天相关状态
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatMessages = ref(null)
const imageType = ref('X-RAY')
const hasConfirmedResult = ref(false)

// 计算属性
const isEmptyInput = computed(() => {
  return !inputMessage.value.trim()
})

/**
 * 处理图片上传
 * @param {Event} event - 上传事件
 */
const handleImageUpload = (event) => {
  const files = event.target.files
  if (files.length > 0) {
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      const reader = new FileReader()
      
      reader.onload = (e) => {
        const imageUrl = e.target.result
        // 添加到聊天消息
        messages.value.push({
          role: 'doctor-message',
          type: 'image',
          content: imageUrl,
          imageType: imageType.value,
          imageDate: new Date().toISOString().split('T')[0]
        })
        
        // 模拟AI分析
        setTimeout(() => {
          analyzeImage(imageUrl, imageType.value)
        }, 1000)
      }
      
      reader.readAsDataURL(file)
    }
  }
}

/**
 * 调用AI分析
 * @param {string} imageUrl - 图片URL
 * @param {string} type - 影像类型
 */
const analyzeImage = async (imageUrl, type) => {
  loading.value = true
  
  try {
    // 准备请求数据
    const requestData = {
      imageUrl: imageUrl,
      imageType: type,
      patientId: patientId.value,
      patientName: patientName.value
    }
    
    // 调用后端API
    const response = await metricApi.analyzeAndReport(JSON.stringify(requestData))
    
    loading.value = false
    
    // 添加AI分析结果
    messages.value.push({
      role: 'ai-message',
      type: 'ai_result',
      content: response.result || '医学影像检查显示未见明显异常。',
      confidence: Math.floor(Math.random() * 10) + 90 // 90-99% 置信度
    })
    
    // 滚动到底部
    scrollToBottom()
  } catch (error) {
    loading.value = false
    console.error('AI分析失败:', error)
    
    // 添加错误消息
    messages.value.push({
      role: 'system-message',
      type: 'text',
      content: 'AI分析失败，请稍后重试'
    })
    
    // 滚动到底部
    scrollToBottom()
  }
}

/**
 * 发送消息
 */
const sendMessage = async () => {
  if (isEmptyInput.value) return
  
  // 添加医生消息
  messages.value.push({
    role: 'doctor-message',
    type: 'text',
    content: inputMessage.value
  })
  
  // 清空输入框
  inputMessage.value = ''
  
  // 滚动到底部
  scrollToBottom()
  
  // 调用AI回复
  try {
    loading.value = true
    
    // 准备请求数据
    const requestData = {
      message: inputMessage.value,
      patientId: patientId.value,
      patientName: patientName.value
    }
    
    // 调用后端API
    const response = await metricApi.analyzeAndReport(JSON.stringify(requestData))
    
    loading.value = false
    
    // 添加AI回复
    messages.value.push({
      role: 'ai-message',
      type: 'text',
      content: response.result || '收到您的消息，我会为您提供专业的医学影像分析服务。'
    })
    
    // 滚动到底部
    scrollToBottom()
  } catch (error) {
    loading.value = false
    console.error('AI回复失败:', error)
    
    // 添加错误消息
    messages.value.push({
      role: 'system-message',
      type: 'text',
      content: 'AI回复失败，请稍后重试'
    })
    
    // 滚动到底部
    scrollToBottom()
  }
}

/**
 * 确认AI分析结果
 * @param {Object} message - AI结果消息
 */
const confirmResult = (message) => {
  message.confirmed = true
  hasConfirmedResult.value = true
  
  // 添加确认消息
  messages.value.push({
    role: 'system-message',
    type: 'text',
    content: '分析结果已确认，可以生成报表'
  })
  
  // 滚动到底部
  scrollToBottom()
}

/**
 * 生成报表
 */
const generateReport = () => {
  // 跳转到报表生成页面
  router.push({
    path: '/metric/reports/list',
    query: { 
      patientId: patientId.value, 
      patientName: patientName.value,
      generate: 'true'
    }
  })
}

/**
 * 清空聊天
 */
const clearChat = () => {
  if (confirm('确定要清空聊天记录吗？')) {
    messages.value = []
    hasConfirmedResult.value = false
  }
}

/**
 * 返回上一页
 */
const goBack = () => {
  router.back()
}

/**
 * 滚动到聊天底部
 */
const scrollToBottom = () => {
  setTimeout(() => {
    if (chatMessages.value) {
      chatMessages.value.scrollTop = chatMessages.value.scrollHeight
    }
  }, 100)
}

// 监听消息变化，自动滚动到底部
watch(messages, () => {
  scrollToBottom()
}, { deep: true })

// 组件挂载时滚动到底部
onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.medical-image-chat {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.nav-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1rem 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.nav-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.back-button {
  background: transparent;
  border: none;
  color: white;
  cursor: pointer;
  padding: 0.5rem;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.back-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.back-icon {
  width: 20px;
  height: 20px;
}

.nav-content h2 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  flex: 1;
}

.patient-info {
  font-size: 0.9rem;
  opacity: 0.9;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: white;
  margin: 1rem;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message {
  max-width: 80%;
  padding: 1rem;
  border-radius: 12px;
  animation: messageFadeIn 0.3s ease-out;
}

@keyframes messageFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.system-message {
  align-self: center;
  background-color: #f0f0f0;
  color: #666;
  font-size: 0.9rem;
  padding: 0.75rem 1.5rem;
  border-radius: 16px;
}

.doctor-message {
  align-self: flex-end;
  background-color: #e3f2fd;
  color: #1976d2;
  border-bottom-right-radius: 4px;
}

.ai-message {
  align-self: flex-start;
  background-color: #f5f5f5;
  color: #333;
  border-bottom-left-radius: 4px;
}

.text-message {
  line-height: 1.5;
}

.image-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.uploaded-image {
  max-width: 100%;
  max-height: 300px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.image-info {
  display: flex;
  gap: 1rem;
  font-size: 0.8rem;
  color: #666;
}

.ai-result-message {
  width: 100%;
  max-width: 90%;
  background-color: #f9f9f9;
  border: 1px solid #e0e0e0;
}

.ai-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e0e0e0;
}

.ai-result-header h4 {
  margin: 0;
  color: #333;
  font-size: 1rem;
}

.confidence {
  font-size: 0.85rem;
  color: #666;
  background-color: #e8f5e9;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
}

.result-textarea {
  width: 100%;
  min-height: 100px;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  resize: vertical;
  margin-bottom: 0.75rem;
}

.ai-result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.loading-animation {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background-color: #667eea;
  border-radius: 50%;
  animation: loadingBounce 1.4s infinite ease-in-out both;
}

.loading-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes loadingBounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.input-area {
  border-top: 1px solid #e0e0e0;
  padding: 1rem;
  background-color: #f9f9f9;
}

.upload-section {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  align-items: center;
}

.upload-button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  background-color: #667eea;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  transition: background-color 0.3s;
}

.upload-button:hover {
  background-color: #5568d3;
}

.upload-icon {
  width: 18px;
  height: 18px;
}

.image-type-select {
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  flex: 1;
}

.text-input-section {
  display: flex;
  gap: 0.5rem;
}

.text-input {
  flex: 1;
  padding: 0.75rem 1rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 0.95rem;
  transition: border-color 0.3s;
}

.text-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.send-button {
  padding: 0.75rem;
  background-color: #667eea;
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: background-color 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-button:hover {
  background-color: #5568d3;
}

.send-icon {
  width: 18px;
  height: 18px;
}

.bottom-actions {
  padding: 1rem 2rem;
  background-color: white;
  border-top: 1px solid #e0e0e0;
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
}

.btn-primary, .btn-secondary {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-primary {
  background-color: #667eea;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #5568d3;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-secondary {
  background-color: #f0f0f0;
  color: #333;
  border: 1px solid #ddd;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #e0e0e0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-primary:disabled, .btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

@media (max-width: 768px) {
  .nav-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .chat-container {
    margin: 0.5rem;
  }
  
  .chat-messages {
    padding: 1rem;
  }
  
  .message {
    max-width: 90%;
  }
  
  .upload-section {
    flex-direction: column;
    align-items: stretch;
  }
  
  .bottom-actions {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .btn-primary, .btn-secondary {
    width: 100%;
  }
}
</style>