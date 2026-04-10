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
          <span class="socket-status" :class="socketStatus">{{ socketStatusLabel }}</span>
        </div>
      </div>
    </div>

    <div class="chat-container">
      <div class="chat-messages" ref="chatMessages">
        <div class="message system-message">
          <div class="message-content">
            <p>欢迎使用医学影像 AI 分析系统。AI Agent 仅支持医疗影像分析、指标解读与报表生成，不提供闲聊或其他通用问答。</p>
          </div>
        </div>

        <div
          v-for="(message, index) in messages"
          :key="message.id || index"
          class="message"
          :class="[message.role, { 'full-width-message': message.type === 'ai_result' }]"
        >
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
                <div class="ai-result-title">
                  <h4>AI分析结果</h4>
                  <p v-if="message.structuredPayload?.summary" class="result-summary">
                    {{ message.structuredPayload.summary }}
                  </p>
                </div>
                <div class="result-badges">
                  <span v-if="message.structuredPayload?.analysisModeLabel" class="analysis-mode">
                    {{ message.structuredPayload.analysisModeLabel }}
                  </span>
                  <span class="confidence">置信度: {{ formatConfidence(message.confidence) }}</span>
                </div>
              </div>

              <div v-if="message.structuredPayload" class="ai-result-grid">
                <section v-if="message.structuredPayload.keyIndicators.length" class="result-panel result-panel-wide">
                  <h5>关键指标</h5>
                  <div class="indicator-grid">
                    <div v-for="indicator in message.structuredPayload.keyIndicators" :key="`${message.id}-${indicator.name}-${indicator.value}`" class="indicator-card">
                      <span class="indicator-name">{{ indicator.name }}</span>
                      <strong class="indicator-value">{{ indicator.value }}</strong>
                      <span v-if="indicator.unit" class="indicator-unit">{{ indicator.unit }}</span>
                      <span v-if="indicator.interpretation" class="indicator-interpretation">{{ indicator.interpretation }}</span>
                    </div>
                  </div>
                </section>

                <section v-if="message.structuredPayload.findings.length" class="result-panel">
                  <h5>主要发现</h5>
                  <ul class="result-list">
                    <li v-for="finding in message.structuredPayload.findings" :key="`${message.id}-${finding}`">{{ finding }}</li>
                  </ul>
                </section>

                <section v-if="message.structuredPayload.recommendations.length" class="result-panel">
                  <h5>处理建议</h5>
                  <ul class="result-list">
                    <li v-for="recommendation in message.structuredPayload.recommendations" :key="`${message.id}-${recommendation}`">{{ recommendation }}</li>
                  </ul>
                </section>

                <section v-if="message.structuredPayload.conclusion" class="result-panel">
                  <h5>综合结论</h5>
                  <p class="panel-text">{{ message.structuredPayload.conclusion }}</p>
                </section>

                <section v-if="message.structuredPayload.limitations.length" class="result-panel">
                  <h5>局限性</h5>
                  <ul class="result-list warning-list">
                    <li v-for="limitation in message.structuredPayload.limitations" :key="`${message.id}-${limitation}`">{{ limitation }}</li>
                  </ul>
                </section>

                <section v-if="message.structuredPayload.reportContent" class="result-panel result-panel-wide">
                  <div class="panel-heading-row">
                    <h5>报表预览</h5>
                    <span v-if="message.structuredPayload.report?.reportType" class="report-type-badge">
                      {{ message.structuredPayload.report.reportType }}
                    </span>
                  </div>
                  <pre class="report-preview">{{ message.structuredPayload.reportContent }}</pre>
                </section>
              </div>

              <div v-else class="ai-result-content">
                <pre class="result-text-block">{{ message.content }}</pre>
              </div>

              <div class="result-status-row">
                <span v-if="message.confirmed" class="save-status success-status">分析结果已保存</span>
                <span v-if="message.reportSaved" class="save-status success-status">报表已保存</span>
                <span v-if="!message.persistableAnalysis" class="save-status hint-status">当前结果不含可保存的结构化分析数据</span>
              </div>

              <div class="ai-result-actions">
                <button
                  @click="confirmResult(message)"
                  class="btn-primary"
                  :disabled="message.confirmed || message.saving || !message.persistableAnalysis"
                >
                  {{ message.confirmed ? '已确认' : message.saving ? '保存中...' : '确认结果' }}
                </button>
              </div>
            </div>
          </div>
        </div>

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

      <div class="input-area">
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
            <option value="XRAY">X射线</option>
            <option value="CT">CT</option>
            <option value="MRI">MRI</option>
            <option value="ULTRASOUND">超声</option>
            <option value="OTHER">其他</option>
          </select>
        </div>

        <div class="text-input-section">
          <input
            v-model="inputMessage"
            type="text"
            placeholder="输入影像说明、指标问题或上传医学影像..."
            class="text-input"
            @keyup.enter="sendMessage"
          />
          <button @click="sendMessage" class="send-button" :disabled="socketStatus !== 'connected'">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="send-icon">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div class="bottom-actions">
      <button @click="generateReport" class="btn-secondary" :disabled="!hasConfirmedResult || generatingReport">
        {{ generatingReport ? '报表保存中...' : '生成报表' }}
      </button>
      <button @click="clearChat" class="btn-secondary">
        清空聊天
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authStore } from '../utils/auth.js'
import { metricApi } from '../utils/api.js'
import { connectMetricAiSocket } from '../utils/ws.js'

const router = useRouter()
const route = useRoute()

const patientId = ref(route.query.patientId || '')
const patientName = ref(route.query.patientName || '未知患者')
const currentUser = ref(authStore.getCurrentUser())

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const chatMessages = ref(null)
const imageType = ref('XRAY')
const socketStatus = ref('connecting')
const socketRef = ref(null)
const confirmedResultMessageId = ref('')
const generatingReport = ref(false)
const historyReady = ref(false)

let conversationPersistTimer = null
let suppressConversationPersistence = false

const socketStatusLabel = computed(() => {
  const labelMap = {
    connecting: '连接中',
    connected: '已连接',
    disconnected: '已断开',
    error: '连接异常',
  }
  return labelMap[socketStatus.value] || '未知状态'
})

const hasConfirmedResult = computed(() => Boolean(getConfirmedResultMessage()))

const createRequestId = () => `req-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`

const getHospitalId = () => currentUser.value?.hospitalId || 'unknown-hospital'

const getConversationId = () => {
  return patientId.value || patientName.value || 'unknown-patient'
}

const normalizeConfidence = (value) => {
  if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) {
    return null
  }
  const normalized = value <= 1 ? value * 100 : value
  return Math.round(normalized)
}

const formatConfidence = (value) => {
  return value == null ? '未提供' : `${value}%`
}

const safeJsonParse = (value) => {
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }

  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

const isRecord = (value) => {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

const normalizeTextList = (value) => {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean)
}

const formatAnalysisMode = (mode) => {
  const modeMap = {
    PIXEL: '像素级分析',
    METADATA_ONLY: '元数据级分析',
  }
  return modeMap[mode] || ''
}

const metricLabelMap = {
  density: '密度',
  size: '异常范围',
  location: '异常位置',
  severity: '风险等级',
  signalIntensity: '信号强度',
  tissueCharacteristics: '组织特征',
  opacity: '透亮度/密度',
  boneStructure: '骨结构',
  echogenicity: '回声性',
  bloodFlow: '血流提示',
  name: '指标',
  value: '值',
  unit: '单位',
  referenceRange: '参考范围',
  confidence: '置信度',
}

const formatValue = (value) => {
  if (typeof value === 'number') {
    return Number.isInteger(value) ? `${value}` : `${Number(value.toFixed(1))}`
  }
  if (Array.isArray(value)) {
    return value.map(formatValue).join('、')
  }
  return value == null ? '-' : `${value}`
}

const extractConfidenceFromMetrics = (metrics) => {
  if (!isRecord(metrics)) {
    return null
  }

  if (typeof metrics.confidence === 'number') {
    return normalizeConfidence(metrics.confidence)
  }

  if (Array.isArray(metrics.metrics)) {
    const values = metrics.metrics
      .map(extractConfidenceFromMetrics)
      .filter((value) => typeof value === 'number')

    if (values.length > 0) {
      return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length)
    }
  }

  return null
}

const extractMetricIndicators = (metrics) => {
  if (!isRecord(metrics)) {
    return []
  }

  if (Array.isArray(metrics.metrics)) {
    return metrics.metrics.flatMap(extractMetricIndicators)
  }

  if (typeof metrics.name === 'string' && metrics.value != null) {
    return [{
      name: metrics.name,
      value: formatValue(metrics.value),
      unit: typeof metrics.unit === 'string' ? metrics.unit : '',
      interpretation: typeof metrics.referenceRange === 'string' ? metrics.referenceRange : '',
    }]
  }

  return Object.entries(metrics)
    .filter(([key, value]) => key !== 'kind' && key !== 'confidence' && value != null && value !== '')
    .map(([key, value]) => ({
      name: metricLabelMap[key] || key,
      value: formatValue(value),
      unit: '',
      interpretation: '',
    }))
}

const normalizeIndicator = (indicator) => {
  if (!isRecord(indicator)) {
    return null
  }

  return {
    name: typeof indicator.name === 'string' ? indicator.name : '指标',
    value: indicator.value == null ? '-' : `${indicator.value}`,
    unit: typeof indicator.unit === 'string' ? indicator.unit : '',
    interpretation: typeof indicator.interpretation === 'string' ? indicator.interpretation : '',
  }
}

const buildSummaryFromAnalysis = (analysis) => {
  if (!isRecord(analysis)) {
    return ''
  }

  const indicators = extractMetricIndicators(analysis.metrics).slice(0, 3)
  if (indicators.length === 0) {
    return `${analysis.patientName || patientName.value} 的影像分析已完成。`
  }

  return indicators.map((indicator) => `${indicator.name}: ${indicator.value}${indicator.unit ? ` ${indicator.unit}` : ''}`).join('，')
}

const normalizeStructuredPayload = (rawPayload) => {
  const parsed = safeJsonParse(rawPayload)
  if (!isRecord(parsed)) {
    return null
  }

  if (typeof parsed.errorCode === 'string') {
    return {
      kind: 'error',
      analysis: null,
      report: null,
      summary: parsed.message || '分析失败',
      analysisModeLabel: '',
      keyIndicators: [],
      findings: [],
      recommendations: [],
      limitations: typeof parsed.detail === 'string' ? [parsed.detail] : [],
      conclusion: '',
      reportContent: '',
    }
  }

  const analysis = isRecord(parsed.analysis) ? parsed.analysis : null
  if (!analysis) {
    return null
  }

  const metricsIndicators = extractMetricIndicators(analysis.metrics)
  const structuredIndicators = Array.isArray(parsed.keyIndicators)
    ? parsed.keyIndicators.map(normalizeIndicator).filter(Boolean)
    : []

  return {
    kind: isRecord(parsed.report) ? 'generated_report' : 'analysis',
    analysis,
    report: isRecord(parsed.report) ? parsed.report : null,
    summary: typeof parsed.summary === 'string' ? parsed.summary : buildSummaryFromAnalysis(analysis),
    analysisModeLabel: formatAnalysisMode(parsed.analysisMode),
    keyIndicators: structuredIndicators.length > 0 ? structuredIndicators : metricsIndicators,
    findings: normalizeTextList(parsed.findings),
    recommendations: normalizeTextList(parsed.recommendations),
    limitations: normalizeTextList(parsed.limitations),
    conclusion: typeof parsed.conclusion === 'string' ? parsed.conclusion : '',
    reportContent: typeof parsed.reportContent === 'string' ? parsed.reportContent : '',
  }
}

const buildResultContent = (structuredPayload, rawPayload, fallbackMessage) => {
  if (structuredPayload?.summary) {
    return structuredPayload.summary
  }

  if (typeof rawPayload === 'string' && rawPayload.trim()) {
    return rawPayload
  }

  return fallbackMessage || 'AI 已完成分析。'
}

const createAiResultMessage = (payload) => {
  const structuredPayload = normalizeStructuredPayload(payload.analysisResult)
  const confidence = structuredPayload?.analysis
    ? normalizeConfidence(extractConfidenceFromMetrics(structuredPayload.analysis.metrics))
    : normalizeConfidence(payload.confidence)

  return {
    id: payload.requestId || createRequestId(),
    role: 'ai-message',
    type: 'ai_result',
    content: buildResultContent(structuredPayload, payload.analysisResult, payload.message),
    confidence,
    structuredPayload,
    persistableAnalysis: Boolean(structuredPayload?.analysis),
    confirmed: false,
    saving: false,
    savedAnalysisId: '',
    reportSaved: false,
  }
}

const restoreMessageFromHistory = (message) => {
  if (!isRecord(message) || typeof message.type !== 'string') {
    return null
  }

  return {
    ...message,
    id: typeof message.id === 'string' && message.id ? message.id : createRequestId(),
    role: typeof message.role === 'string' ? message.role : 'system-message',
    content: typeof message.content === 'string' ? message.content : '',
    structuredPayload: isRecord(message.structuredPayload) ? message.structuredPayload : null,
    confidence: typeof message.confidence === 'number' ? message.confidence : null,
    persistableAnalysis: message.type === 'ai_result'
      ? Boolean(message.persistableAnalysis || message.structuredPayload?.analysis)
      : Boolean(message.persistableAnalysis),
    confirmed: Boolean(message.confirmed),
    saving: false,
    savedAnalysisId: typeof message.savedAnalysisId === 'string' ? message.savedAnalysisId : '',
    reportSaved: Boolean(message.reportSaved),
    imageType: typeof message.imageType === 'string' ? message.imageType : '',
    imageDate: typeof message.imageDate === 'string' ? message.imageDate : '',
  }
}

const serializeMessageForHistory = (message) => {
  if (!isRecord(message) || message.ephemeral) {
    return null
  }

  const { saving, ephemeral, ...serializableMessage } = message
  return serializableMessage
}

const persistConversationHistory = async () => {
  if (!historyReady.value || suppressConversationPersistence) {
    return
  }

  const serializableMessages = messages.value
    .map(serializeMessageForHistory)
    .filter(Boolean)

  await metricApi.saveConversationHistory(getConversationId(), {
    conversationId: getConversationId(),
    patientName: patientName.value,
    hospitalId: getHospitalId(),
    confirmedResultMessageId: confirmedResultMessageId.value || null,
    messages: serializableMessages,
    updatedAt: new Date().toISOString(),
  })
}

const scheduleConversationPersist = () => {
  if (!historyReady.value || suppressConversationPersistence) {
    return
  }

  if (conversationPersistTimer) {
    clearTimeout(conversationPersistTimer)
  }

  conversationPersistTimer = window.setTimeout(() => {
    conversationPersistTimer = null
    persistConversationHistory().catch((error) => {
      console.error('保存对话历史失败:', error)
    })
  }, 300)
}

const flushConversationHistory = async () => {
  if (conversationPersistTimer) {
    clearTimeout(conversationPersistTimer)
    conversationPersistTimer = null
  }

  await persistConversationHistory()
}

const loadConversationHistory = async () => {
  historyReady.value = false
  try {
    const response = await metricApi.getConversationHistory(getConversationId(), {
      hospitalId: getHospitalId(),
      patientName: patientName.value,
    })
    const restoredMessages = Array.isArray(response?.messages)
      ? response.messages.map(restoreMessageFromHistory).filter(Boolean)
      : []
    messages.value = restoredMessages
    confirmedResultMessageId.value = typeof response?.confirmedResultMessageId === 'string'
      ? response.confirmedResultMessageId
      : ''
  } catch (error) {
    console.error('加载对话历史失败:', error)
    messages.value = []
    confirmedResultMessageId.value = ''
  } finally {
    historyReady.value = true
    scrollToBottom()
  }
}

const buildPersistableAnalysis = (message) => {
  const analysis = message.structuredPayload?.analysis
  if (!isRecord(analysis)) {
    return null
  }

  const now = new Date().toISOString()
  return {
    ...analysis,
    hospitalId: analysis.hospitalId || getHospitalId(),
    patientId: analysis.patientId || patientId.value,
    patientName: analysis.patientName || patientName.value,
    imageId: analysis.imageId || `IMG-${patientId.value || Date.now()}`,
    status: analysis.status || 'completed',
    createdAt: analysis.createdAt || now,
    completedAt: analysis.completedAt || now,
    errorMessage: analysis.errorMessage || null,
  }
}

const buildPersistableReport = (message) => {
  const report = message.structuredPayload?.report
  if (!isRecord(report)) {
    return null
  }

  const analysisId = message.savedAnalysisId || buildPersistableAnalysis(message)?.id
  if (!analysisId) {
    return null
  }

  const now = new Date().toISOString()
  return {
    ...report,
    hospitalId: report.hospitalId || getHospitalId(),
    patientId: report.patientId || patientId.value,
    patientName: report.patientName || patientName.value,
    analysisIds: [analysisId],
    reportType: report.reportType || `${imageType.value}_AI_REPORT`,
    status: report.status || 'generated',
    createdAt: report.createdAt || now,
    generatedAt: report.generatedAt || now,
    errorMessage: report.errorMessage || null,
  }
}

const getConfirmedResultMessage = () => {
  if (confirmedResultMessageId.value) {
    const matched = messages.value.find((message) => message.id === confirmedResultMessageId.value && message.confirmed)
    if (matched) {
      return matched
    }
  }

  return [...messages.value].reverse().find((message) => message.type === 'ai_result' && message.confirmed) || null
}

const disconnectSocket = () => {
  if (socketRef.value) {
    socketRef.value.onopen = null
    socketRef.value.onmessage = null
    socketRef.value.onclose = null
    socketRef.value.onerror = null
    socketRef.value.close()
    socketRef.value = null
  }
}

const connectSocket = () => {
  disconnectSocket()
  const socket = connectMetricAiSocket()
  socketRef.value = socket
  socketStatus.value = 'connecting'

  socket.onopen = () => {
    socketStatus.value = 'connected'
    messages.value.push({
      id: createRequestId(),
      role: 'system-message',
      type: 'text',
      content: 'AI 会话已连接，可继续上传医学影像，或咨询影像所见、指标分析与报表相关问题。',
      ephemeral: true,
    })
  }

  socket.onmessage = (event) => {
    try {
      const payload = JSON.parse(event.data)
      if (payload.type === 'processing') {
        loading.value = true
        return
      }

      if (payload.type === 'connected') {
        return
      }

      if (payload.type === 'error') {
        loading.value = false
        messages.value.push({
          id: createRequestId(),
          role: 'system-message',
          type: 'text',
          content: payload.message || 'AI 会话处理失败，请稍后重试。',
        })
        return
      }

      if (payload.type === 'ai_response') {
        loading.value = false
        if (payload.analysisResult) {
          messages.value.push(createAiResultMessage(payload))
        } else {
          messages.value.push({
            id: createRequestId(),
            role: 'ai-message',
            type: 'text',
            content: payload.message || 'AI 已收到请求。',
          })
        }
      }
    } catch (_error) {
      loading.value = false
      messages.value.push({
        id: createRequestId(),
        role: 'system-message',
        type: 'text',
        content: '收到无法解析的服务端消息。',
      })
    }

    scrollToBottom()
  }

  socket.onclose = () => {
    socketStatus.value = 'disconnected'
  }

  socket.onerror = () => {
    socketStatus.value = 'error'
    loading.value = false
  }
}

const sendSocketPayload = (payload) => {
  if (!socketRef.value || socketRef.value.readyState !== WebSocket.OPEN) {
    messages.value.push({
      id: createRequestId(),
      role: 'system-message',
      type: 'text',
      content: 'WebSocket 连接未建立，无法发送到 AI Agent。',
    })
    return false
  }

  loading.value = true
  socketRef.value.send(JSON.stringify(payload))
  return true
}

const buildBasePayload = () => {
  return {
    requestId: createRequestId(),
    patientId: patientId.value,
    patientName: patientName.value,
    hospitalId: getHospitalId(),
  }
}

const handleImageUpload = (event) => {
  const files = event.target.files
  if (!files?.length) {
    return
  }

  Array.from(files).forEach((file) => {
    const reader = new FileReader()
    reader.onload = (loadEvent) => {
      const imageUrl = loadEvent.target?.result
      if (!imageUrl) {
        return
      }

      messages.value.push({
        id: createRequestId(),
        role: 'doctor-message',
        type: 'image',
        content: imageUrl,
        imageType: imageType.value,
        imageDate: new Date().toISOString().split('T')[0],
      })

      sendSocketPayload({
        ...buildBasePayload(),
        type: 'image',
        imageData: imageUrl,
        imageType: imageType.value,
      })
      scrollToBottom()
    }
    reader.readAsDataURL(file)
  })

  event.target.value = ''
}

const sendMessage = () => {
  const text = inputMessage.value.trim()
  if (!text) {
    return
  }

  messages.value.push({
    id: createRequestId(),
    role: 'doctor-message',
    type: 'text',
    content: text,
  })

  inputMessage.value = ''
  scrollToBottom()

  sendSocketPayload({
    ...buildBasePayload(),
    type: 'chat',
    message: text,
  })
}

const confirmResult = async (message) => {
  if (message.confirmed || message.saving) {
    return
  }

  const analysisPayload = buildPersistableAnalysis(message)
  if (!analysisPayload) {
    window.alert('当前结果不包含可保存的结构化分析数据。')
    return
  }

  message.saving = true
  try {
    const response = await metricApi.createAnalysisResult(analysisPayload)
    message.confirmed = true
    message.savedAnalysisId = response?.id || analysisPayload.id
    confirmedResultMessageId.value = message.id
    messages.value.push({
      id: createRequestId(),
      role: 'system-message',
      type: 'text',
      content: '分析结果已确认，并已同步到分析结果管理。',
    })
    await flushConversationHistory()
    scrollToBottom()
  } catch (error) {
    window.alert(error.message || '保存分析结果失败，请重试。')
  } finally {
    message.saving = false
  }
}

const generateReport = async () => {
  const message = getConfirmedResultMessage()
  if (!message) {
    return
  }

  if (message.reportSaved) {
    router.push({
      path: '/metric/reports/list',
      query: {
        patientId: patientId.value,
        patientName: patientName.value,
        generate: 'true',
      },
    })
    return
  }

  const reportPayload = buildPersistableReport(message)
  if (!reportPayload) {
    window.alert('当前结果不包含可保存的报表数据，请先完成一次结构化分析。')
    return
  }

  generatingReport.value = true
  try {
    await metricApi.createReport(reportPayload)
    message.reportSaved = true
    messages.value.push({
      id: createRequestId(),
      role: 'system-message',
      type: 'text',
      content: '报表已生成，并已同步到报表管理。',
    })
    await flushConversationHistory()
    router.push({
      path: '/metric/reports/list',
      query: {
        patientId: patientId.value,
        patientName: patientName.value,
        generate: 'true',
      },
    })
  } catch (error) {
    window.alert(error.message || '生成报表失败，请重试。')
  } finally {
    generatingReport.value = false
  }
}

const clearChat = async () => {
  if (window.confirm('确定要清空聊天记录吗？')) {
    suppressConversationPersistence = true
    messages.value = []
    loading.value = false
    confirmedResultMessageId.value = ''
    generatingReport.value = false
    try {
      await metricApi.clearConversationHistory(getConversationId(), getHospitalId())
    } catch (error) {
      console.error('清空对话历史失败:', error)
      window.alert(error.message || '已清空当前界面，但删除历史记录失败。')
    } finally {
      suppressConversationPersistence = false
    }
  }
}

const goBack = () => {
  router.back()
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (chatMessages.value) {
      chatMessages.value.scrollTop = chatMessages.value.scrollHeight
    }
  }, 80)
}

const syncConversationContext = async () => {
  currentUser.value = authStore.getCurrentUser()
  patientId.value = route.query.patientId || ''
  patientName.value = route.query.patientName || '未知患者'
  loading.value = false
  generatingReport.value = false
  await loadConversationHistory()
  connectSocket()
}

watch(messages, () => {
  scrollToBottom()
}, { deep: true })

watch([messages, confirmedResultMessageId], () => {
  scheduleConversationPersist()
}, { deep: true })

watch(
  () => [route.query.patientId, route.query.patientName],
  async ([nextPatientId, nextPatientName], [previousPatientId, previousPatientName]) => {
    if (nextPatientId === previousPatientId && nextPatientName === previousPatientName) {
      return
    }

    if (historyReady.value && !suppressConversationPersistence) {
      await flushConversationHistory()
    }

    await syncConversationContext()
  },
)

onMounted(async () => {
  await syncConversationContext()
  scrollToBottom()
})

onUnmounted(() => {
  if (conversationPersistTimer) {
    clearTimeout(conversationPersistTimer)
    conversationPersistTimer = null
  }
  flushConversationHistory().catch((error) => {
    console.error('组件卸载时保存对话历史失败:', error)
  })
  disconnectSocket()
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
  display: flex;
  gap: 0.75rem;
  align-items: center;
  font-size: 0.9rem;
  opacity: 0.95;
}

.socket-status {
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.16);
}

.socket-status.connected {
  background: rgba(34, 197, 94, 0.22);
}

.socket-status.connecting {
  background: rgba(250, 204, 21, 0.25);
}

.socket-status.disconnected,
.socket-status.error {
  background: rgba(248, 113, 113, 0.26);
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

.full-width-message {
  align-self: stretch;
  width: 100%;
  max-width: 100%;
  padding: 0;
  background: transparent;
  border-radius: 0;
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
  white-space: pre-wrap;
}

.image-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.uploaded-image {
  max-width: 100%;
  max-height: 320px;
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
  width: min(1120px, 100%);
  margin-right: auto;
  background: linear-gradient(180deg, #fbfdff 0%, #f3f7ff 100%);
  border: 1px solid #d8e3ff;
  border-radius: 18px;
  padding: 1.25rem;
  box-shadow: 0 16px 40px rgba(60, 89, 173, 0.08);
}

.ai-result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid #e0e8ff;
}

.ai-result-title {
  min-width: 0;
}

.ai-result-header h4 {
  margin: 0;
  color: #22304f;
  font-size: 1.1rem;
}

.result-summary {
  margin: 0.4rem 0 0;
  color: #5a6480;
  line-height: 1.6;
}

.result-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

.analysis-mode,
.confidence,
.report-type-badge {
  font-size: 0.82rem;
  color: #30456b;
  background-color: #e8efff;
  padding: 0.35rem 0.8rem;
  border-radius: 999px;
  white-space: nowrap;
}

.ai-result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.result-panel {
  background: white;
  border: 1px solid #e4ebff;
  border-radius: 14px;
  padding: 1rem;
}

.result-panel-wide {
  grid-column: 1 / -1;
}

.result-panel h5 {
  margin: 0 0 0.85rem;
  color: #22304f;
  font-size: 0.98rem;
}

.panel-heading-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}

.panel-text {
  margin: 0;
  line-height: 1.7;
  color: #3e4b68;
}

.indicator-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.85rem;
}

.indicator-card {
  background: #f7f9ff;
  border: 1px solid #e2e8ff;
  border-radius: 12px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.indicator-name {
  color: #5f6b89;
  font-size: 0.84rem;
}

.indicator-value {
  color: #1e2f56;
  font-size: 1rem;
}

.indicator-unit,
.indicator-interpretation {
  color: #6a7594;
  font-size: 0.82rem;
}

.result-list {
  margin: 0;
  padding-left: 1.2rem;
  color: #3e4b68;
  line-height: 1.7;
}

.warning-list {
  color: #8a5b00;
}

.report-preview,
.result-text-block {
  margin: 0;
  background: #0f172a;
  color: #dbeafe;
  border-radius: 12px;
  padding: 1rem;
  overflow: auto;
  max-height: 360px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 0.88rem;
}

.ai-result-content {
  margin-bottom: 0.75rem;
}

.result-status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1rem;
}

.save-status {
  font-size: 0.84rem;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
}

.success-status {
  background: #e8f7ec;
  color: #1f7a38;
}

.hint-status {
  background: #fff4e5;
  color: #8a5b00;
}

.ai-result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1rem;
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

.send-button:hover:not(:disabled) {
  background-color: #5568d3;
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

.btn-primary,
.btn-secondary {
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

.btn-primary:disabled,
.btn-secondary:disabled {
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

  .patient-info {
    flex-direction: column;
    align-items: flex-start;
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

  .full-width-message {
    max-width: 100%;
  }

  .ai-result-message {
    width: 100%;
    padding: 1rem;
  }

  .ai-result-header,
  .panel-heading-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .result-badges {
    justify-content: flex-start;
  }

  .ai-result-grid {
    grid-template-columns: 1fr;
  }

  .upload-section {
    flex-direction: column;
    align-items: stretch;
  }

  .bottom-actions {
    flex-direction: column;
    gap: 0.5rem;
  }

  .btn-primary,
  .btn-secondary {
    width: 100%;
  }
}
</style>
