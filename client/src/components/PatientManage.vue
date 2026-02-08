<template>
  <div class="patient-manage-container">
    <div class="nav-bar">
      <h2>患者管理</h2>
    </div>

    <div class="tab-content">
      <div class="toolbar">
        <div class="actions">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索患者..."
            @input="handleSearch"
          />
          <select v-model="statusFilter" @change="fetchPatients">
            <option value="">全部状态</option>
            <option value="Active">激活</option>
            <option value="Discharged">出院</option>
            <option value="Deceased">死亡</option>
          </select>
          <button @click="handleAddPatient" class="btn-primary">新增患者</button>
          <button @click="fetchPatients">刷新</button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="content">
        <div class="table-container">
          <table class="patient-table">
            <thead>
              <tr>
                <th>患者姓名</th>
                <th>性别</th>
                <th>年龄</th>
                <th>血型</th>
                <th>主治医生</th>
                <th>状态</th>
                <th>最近就诊</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="patients.length > 0" v-for="patient in patients" :key="patient.id">
                <td>{{ patient.name }}</td>
                <td>{{ formatGender(patient.gender) }}</td>
                <td>{{ patient.age || '-' }}</td>
                <td>{{ formatBloodType(patient.bloodType) }}</td>
                <td>{{ patient.attendingDoctorName || patient.doctorName || patient.attendingDoctorId || patient.doctorId || '-' }}</td>
                <td>
                  <span class="status-badge" :class="patient.status">{{ formatStatus(patient.status) }}</span>
                </td>
                <td>{{ formatDate(patient.lastVisitDate) }}</td>
                <td class="actions">
                  <div class="action-buttons">
                    <button @click="viewPatient(patient)" class="btn-small btn-view">查看</button>
                    <button @click="editPatient(patient)" class="btn-small btn-edit">编辑</button>
                    <button @click="showChangeStatus(patient)" class="btn-small btn-status">状态</button>
                    <button @click="confirmDelete(patient)" class="btn-small btn-delete">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="patients.length === 0">
                <td colspan="8" class="empty-state">
                  <div class="empty-content">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                      <circle cx="9" cy="7" r="4"></circle>
                      <line x1="23" y1="11" x2="17" y2="11"></line>
                      <line x1="23" y1="7" x2="17" y2="7"></line>
                    </svg>
                    <p>暂无患者数据</p>
                    <p v-if="!searchKeyword && !statusFilter">点击上方"新增患者"按钮添加</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button
            :disabled="pagination.page === 0"
            @click="changePage(pagination.page - 1)"
          >
            上一页
          </button>
          <span>第 {{ pagination.page + 1 }} / {{ pagination.totalPages }} 页</span>
          <span>共 {{ pagination.total }} 条</span>
          <button
            :disabled="pagination.page >= pagination.totalPages - 1"
            @click="changePage(pagination.page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑患者对话框 -->
    <div v-if="showAddPatient || showEditPatient" class="modal-overlay" @click="closeModals">
      <div class="modal large-modal" @click.stop>
        <h3>{{ showEditPatient ? '编辑患者' : '新增患者' }}</h3>
        <form @submit.prevent="handlePatientSubmit">
          <!-- 基本信息 -->
          <div class="form-section">
            <h4>基本信息</h4>
            <div class="form-group">
              <label>患者姓名 *</label>
              <div class="input-with-clear">
                <input v-model="patientForm.name" type="text" required placeholder="请输入患者姓名" />
                <button v-if="patientForm.name" type="button" class="clear-btn" @click="patientForm.name = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>性别 *</label>
              <select v-model="patientForm.gender" required>
                <option value="M">男</option>
                <option value="F">女</option>
              </select>
            </div>
            <div class="form-group">
              <label>出生日期</label>
              <div class="info-display">
                <input type="text" :value="patientForm.birthDate" readonly placeholder="由身份证号自动生成" />
              </div>
            </div>
            <div class="form-group">
              <label>身份证号</label>
              <div class="info-display" v-if="showEditPatient">
                <input type="text" :value="patientForm.idCard" readonly placeholder="身份证号" />
              </div>
              <input
                v-else
                v-model="patientForm.idCard"
                type="text"
                placeholder="请输入身份证号"
                maxlength="21"
                @input="handleIdCardInput"
              />
            </div>
            <div class="form-group">
              <label>联系电话 *</label>
              <div class="input-with-clear">
                <input
                  v-model="patientForm.phone"
                  type="tel"
                  required
                  @input="handlePhoneInput"
                  placeholder="请输入11位手机号码"
                />
                <button v-if="patientForm.phone" type="button" class="clear-btn" @click="patientForm.phone = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>血型</label>
              <div class="info-display" v-if="showEditPatient">
                <input type="text" :value="formatBloodType(patientForm.bloodType)" readonly placeholder="血型" />
              </div>
              <select v-else v-model="patientForm.bloodType">
                <option value="">未知</option>
                <option value="A">A型</option>
                <option value="B">B型</option>
                <option value="O">O型</option>
                <option value="AB">AB型</option>
              </select>
            </div>
          </div>

          <!-- 就诊信息 -->
          <div class="form-section">
            <h4>就诊信息</h4>
            <div class="form-group">
              <label>患者编号 *</label>
              <div class="info-display" v-if="showEditPatient">
                <input type="text" :value="patientForm.patientId" readonly placeholder="患者编号" />
              </div>
              <input
                v-else
                v-model="patientForm.patientId"
                type="text"
                placeholder="请输入患者病历号"
                required
              />
            </div>
            <div class="form-group">
              <label>主治医院</label>
              <div class="info-display">
                <input v-model="patientForm.hospitalId" type="hidden" />
                <input
                  type="text"
                  :value="currentHospital?.name || ''"
                  readonly
                  placeholder="加载中..."
                />
              </div>
            </div>
            <div class="form-group">
              <label>科室</label>
              <div class="info-display">
                <input v-model="patientForm.department" type="hidden" />
                <input
                  type="text"
                  :value="currentDoctor?.deptName || currentDoctor?.deptCode || '加载中...'"
                  readonly
                  placeholder="加载中..."
                />
              </div>
            </div>
            <div class="form-group">
              <label>主治医生 *</label>
              <div class="info-display">
                <input v-model="patientForm.attendingDoctorId" type="hidden" />
                <input
                  type="text"
                  :value="currentDoctor ? currentDoctor.realName || currentDoctor.fullName || '医生' : '加载中...'"
                  readonly
                  placeholder="加载中..."
                />
              </div>
            </div>
            <div class="form-group">
              <label>主诉</label>
              <div class="input-with-clear textarea-container">
                <textarea
                  v-model="patientForm.chiefComplaint"
                  rows="3"
                  placeholder="请输入患者主诉（当前症状）"
                ></textarea>
                <button v-if="patientForm.chiefComplaint" type="button" class="clear-btn" @click="patientForm.chiefComplaint = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>首次就诊时间</label>
              <div class="input-with-clear">
                <input
                  v-model="patientForm.firstVisitDate"
                  type="datetime-local"
                  placeholder="选择首次就诊时间"
                />
                <button v-if="patientForm.firstVisitDate" type="button" class="clear-btn" @click="patientForm.firstVisitDate = ''">×</button>
              </div>
            </div>
          </div>

          <!-- 体格检查 -->
          <div class="form-section">
            <h4>体格检查</h4>
            <div class="form-group">
              <label>身高 (cm)</label>
              <div class="input-with-clear">
                <input
                  v-model="patientForm.heightCm"
                  type="number"
                  min="0"
                  max="300"
                  placeholder="请输入身高"
                />
                <button v-if="patientForm.heightCm" type="button" class="clear-btn" @click="patientForm.heightCm = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>体重 (kg)</label>
              <div class="input-with-clear">
                <input
                  v-model="patientForm.weightKg"
                  type="number"
                  min="0"
                  max="500"
                  step="0.1"
                  placeholder="请输入体重"
                />
                <button v-if="patientForm.weightKg" type="button" class="clear-btn" @click="patientForm.weightKg = ''">×</button>
              </div>
            </div>
          </div>

          <!-- 病史信息 -->
          <div class="form-section">
            <h4>病史信息</h4>
            <div class="form-group">
              <label>过敏史</label>
              <div class="input-with-clear textarea-container">
                <textarea
                  v-model="patientForm.allergies"
                  rows="2"
                  placeholder="请输入过敏史，多个过敏原用逗号分隔"
                ></textarea>
                <button v-if="patientForm.allergies" type="button" class="clear-btn" @click="patientForm.allergies = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>既往病史</label>
              <div class="input-with-clear textarea-container">
                <textarea
                  v-model="patientForm.medicalHistory"
                  rows="3"
                  placeholder="请输入既往病史"
                ></textarea>
                <button v-if="patientForm.medicalHistory" type="button" class="clear-btn" @click="patientForm.medicalHistory = ''">×</button>
              </div>
            </div>
            <div class="form-group">
              <label>家族病史</label>
              <div class="input-with-clear textarea-container">
                <textarea
                  v-model="patientForm.familyHistory"
                  rows="3"
                  placeholder="请输入家族病史"
                ></textarea>
                <button v-if="patientForm.familyHistory" type="button" class="clear-btn" @click="patientForm.familyHistory = ''">×</button>
              </div>
            </div>
          </div>

          <div v-if="formError" class="error-message">{{ formError }}</div>
          <div class="modal-actions">
            <button type="button" @click="closeModals">取消</button>
            <button type="submit" :disabled="formLoading">
              {{ formLoading ? '处理中...' : '确认' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 查看患者详情对话框 -->
    <div v-if="showViewPatient" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>患者详情</h3>
        <div class="patient-detail">
          <div class="detail-row">
            <span class="label">患者ID:</span>
            <span class="value">{{ selectedPatient?.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">患者姓名:</span>
            <span class="value">{{ selectedPatient?.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">性别:</span>
            <span class="value">{{ formatGender(selectedPatient?.gender) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">年龄:</span>
            <span class="value">{{ selectedPatient?.age || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">血型:</span>
            <span class="value">{{ formatBloodType(selectedPatient?.bloodType) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">联系电话:</span>
            <span class="value">{{ selectedPatient?.phone }}</span>
          </div>
          <div class="detail-row">
            <span class="label">主治医生:</span>
            <span class="value">{{ selectedPatient?.attendingDoctorName || selectedPatient?.doctorId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">所属医院:</span>
            <span class="value">{{ selectedPatient?.hospitalId || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">状态:</span>
            <span class="value status-badge" :class="selectedPatient?.status">
              {{ formatStatus(selectedPatient?.status) }}
            </span>
          </div>
          <div class="detail-row">
            <span class="label">入院时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.admissionDate) }}</span>
          </div>
          <div v-if="selectedPatient?.dischargeDate" class="detail-row">
            <span class="label">出院时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.dischargeDate) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.createdAt) }}</span>
          </div>
          <div v-if="selectedPatient?.updatedAt" class="detail-row">
            <span class="label">更新时间:</span>
            <span class="value">{{ formatDate(selectedPatient?.updatedAt) }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button type="button" @click="closeModals">关闭</button>
        </div>
      </div>
    </div>

    <!-- 更改状态对话框 -->
    <div v-if="showChangeStatusModal" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>更改患者状态</h3>
        <p>为患者 <strong>{{ selectedPatient?.name }}</strong> 更改状态</p>
        <form @submit.prevent="handleStatusChange">
          <div class="form-group">
            <label>新状态 *</label>
            <select v-model="newStatus" required>
              <option value="Active">激活</option>
              <option value="Discharged">出院</option>
              <option value="Deceased">死亡</option>
            </select>
          </div>
          <div v-if="formError" class="error-message">{{ formError }}</div>
          <div class="modal-actions">
            <button type="button" @click="closeModals">取消</button>
            <button type="submit" :disabled="formLoading">
              {{ formLoading ? '处理中...' : '确认更改' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="closeModals">
      <div class="modal" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除患者 <strong>{{ selectedPatient?.name }}</strong> 吗？</p>
        <p class="warning">此操作不可恢复！</p>
        <div class="modal-actions">
          <button type="button" @click="closeModals">取消</button>
          <button type="button" @click="handleDelete" class="btn-danger" :disabled="formLoading">
            {{ formLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { patientApi, authApi } from '../utils/api.js'
import { authStore } from '../utils/auth.js'

const route = useRoute()

// 患者管理相关状态
const patients = ref([])
const loading = ref(false)
const error = ref('')
const formLoading = ref(false)
const formError = ref('')

const searchKeyword = ref('')
const statusFilter = ref('')

const pagination = ref({
  page: 0,
  size: 10,
  totalPages: 1,
  total: 0,
})

// 对话框状态
const showAddPatient = ref(false)
const showEditPatient = ref(false)
const showViewPatient = ref(false)
const showChangeStatusModal = ref(false)
const showDeleteConfirm = ref(false)

// 表单数据
const patientForm = ref({
  name: '',
  gender: 'M',
  birthDate: '',
  bloodType: 'Unknown',
  phone: '',
  idCard: '',
  doctorId: '',
  hospitalId: '',
  patientId: '',  // External patient ID
  department: '', // Department code
  attendingDoctorId: '', // Attending doctor ID (backend expects this name)
  attendingDoctorName: '', // Attending doctor name for display
  // 新增字段
  chiefComplaint: '',
  heightCm: '',
  weightKg: '',
  allergies: '',
  medicalHistory: '',
  familyHistory: '',
  firstVisitDate: '',
})

// 医院和医生信息
const currentHospital = ref(null)
const currentDoctor = ref(null)

const selectedPatient = ref(null)
const newStatus = ref('Active')

// 患者管理相关方法
const fetchPatients = async () => {
  loading.value = true
  error.value = ''

  try {
    const params = {
      page: pagination.value.page,
      size: pagination.value.size,
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }

    const result = await patientApi.getPatients(params)
    console.log('患者列表响应:', result)

    // 兼容不同的响应格式
    console.log('患者列表响应类型:', typeof result)
    console.log('患者列表响应是否为数组:', Array.isArray(result))
    console.log('患者列表响应长度:', Array.isArray(result) ? result.length : 0)
    
    if (result && typeof result === 'object') {
      if (Array.isArray(result)) {
        patients.value = result
        pagination.value.total = result.length
        pagination.value.totalPages = Math.ceil(result.length / pagination.value.size) || 1
      } else {
        patients.value = result.patients || result.content || []
        pagination.value.total = result.totalElements || result.total || patients.value.length
        pagination.value.totalPages = Math.ceil(pagination.value.total / pagination.value.size) || 1
      }
    } else if (Array.isArray(result)) {
      patients.value = result
      pagination.value.total = result.length
      pagination.value.totalPages = Math.ceil(result.length / pagination.value.size) || 1
    } else {
      patients.value = []
      pagination.value.total = 0
      pagination.value.totalPages = 1
    }
    
    console.log('最终患者列表:', patients.value)
    console.log('最终分页信息:', pagination.value)
  } catch (err) {
    console.error('获取患者列表失败:', err)
    error.value = `获取患者列表失败: ${err.message || '未知错误'}`
    patients.value = []
    pagination.value.total = 0
    pagination.value.totalPages = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    fetchPatients()
    return
  }

  loading.value = true
  error.value = ''

  try {
    const params = {
      keyword: searchKeyword.value,
      page: 0,
      size: 20,
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const result = await patientApi.searchPatients(params)

    // 兼容不同的响应格式
    if (result && typeof result === 'object') {
      patients.value = result.patients || result.content || []
      pagination.value.total = result.totalElements || result.total || patients.value.length
      pagination.value.totalPages = Math.ceil(pagination.value.total / pagination.value.size) || 1
    } else if (Array.isArray(result)) {
      patients.value = result
      pagination.value.total = result.length
      pagination.value.totalPages = 1
    }
  } catch (err) {
    console.error('搜索失败:', err)
    error.value = err.message || '搜索失败'
    patients.value = []
    pagination.value.total = 0
    pagination.value.totalPages = 0
  } finally {
    loading.value = false
  }
}

const changePage = (newPage) => {
  pagination.value.page = newPage
  fetchPatients()
}

const viewPatient = (patient) => {
  selectedPatient.value = patient
  showViewPatient.value = true
}

const editPatient = (patient) => {
  selectedPatient.value = patient
  patientForm.value = {
    name: patient.name,
    gender: patient.gender,
    birthDate: patient.birthDate ? patient.birthDate.replace(/-/g, '/') : '', // Convert from ISO format to display format
    bloodType: patient.bloodType || '',
    phone: patient.phone,
    idCard: patient.idCard || '',
    doctorId: patient.doctorId || '',
    hospitalId: patient.hospitalId || '',
    patientId: patient.patientId || '',  // External patient ID
    department: patient.department || '', // Department
    attendingDoctorId: patient.attendingDoctorId || patient.doctorId || '', // Attending doctor ID
    attendingDoctorName: patient.attendingDoctorName || '', // Attending doctor name
    // 新增字段
    chiefComplaint: patient.chiefComplaint || '',
    heightCm: patient.heightCm ? String(patient.heightCm) : '',
    weightKg: patient.weightKg ? String(patient.weightKg) : '',
    allergies: patient.allergies || '',
    medicalHistory: patient.medicalHistory || '',
    familyHistory: patient.familyHistory || '',
    firstVisitDate: patient.firstVisitDate ? formatDateTimeForInput(patient.firstVisitDate) : '',
  }
  showEditPatient.value = true
}

const showChangeStatus = (patient) => {
  selectedPatient.value = patient
  newStatus.value = patient.status
  showChangeStatusModal.value = true
}

const confirmDelete = (patient) => {
  selectedPatient.value = patient
  showDeleteConfirm.value = true
}

const handlePatientSubmit = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    // 验证电话号码
    if (!validatePhoneNumber(patientForm.value.phone)) {
      throw new Error('请输入有效的11位手机号码')
    }

    // 验证必要字段
    let patientId = patientForm.value.patientId?.trim()
    if (!patientId) {
      throw new Error('请输入患者编号')
    }

    // 验证患者姓名
    let patientName = patientForm.value.name?.trim()
    if (!patientName) {
      throw new Error('请输入患者姓名')
    }

    // Check if department is available either from form or from current user
    const department = patientForm.value.department || currentDoctor.value?.deptCode
    if (!department) {
      throw new Error('无法获取科室信息，请重新登录')
    }

    // 验证医生ID
    const attendingDoctorId = patientForm.value.attendingDoctorId || patientForm.value.doctorId || currentDoctor.value?.id
    if (!attendingDoctorId) {
      throw new Error('无法获取医生信息，请重新登录')
    }

    // 验证医院ID
    let hospitalId = (patientForm.value.hospitalId || currentHospital.value?.id)?.trim()
    if (!hospitalId) {
      throw new Error('无法获取医院信息，请重新登录')
    }

    // 验证字段长度（根据数据库表定义）
    if (hospitalId.length > 50) {
      throw new Error('医院ID长度不能超过50个字符')
    }

    if (patientId.length > 50) {
      throw new Error('患者编号长度不能超过50个字符')
    }

    if (attendingDoctorId.length > 36) {
      throw new Error('医生ID长度不能超过36个字符')
    }

    if (department.length > 50) {
      throw new Error('科室代码长度不能超过50个字符')
    }

    // 验证身份证号格式（如果填写了）
    if (patientForm.value.idCard) {
      const idCard = patientForm.value.idCard.trim()
      // 清除非数字和X字符后再检查长度
      const cleanedIdCard = idCard.replace(/[^0-9Xx]/g, '')
      if (cleanedIdCard.length !== 18 && cleanedIdCard.length !== 15) {
        throw new Error('身份证号格式不正确')
      }
    }

    // 验证身高体重
    if (patientForm.value.heightCm) {
      const height = parseInt(patientForm.value.heightCm)
      if (isNaN(height) || height < 0 || height > 300) {
        throw new Error('身高数值无效')
      }
    }

    if (patientForm.value.weightKg) {
      const weight = parseFloat(patientForm.value.weightKg)
      if (isNaN(weight) || weight < 0 || weight > 500) {
        throw new Error('体重数值无效')
      }
    }

    // 出生日期由身份证号自动生成，无需单独验证


    // 格式化电话号码
    patientForm.value.phone = formatPhoneNumber(patientForm.value.phone)

    // 准备发送给后端的数据
    const phoneDigits = (patientForm.value.phone || '').replace(/\D/g, '')
    const birthDateIso = patientForm.value.birthDate ? formatBirthDateForBackend(patientForm.value.birthDate) : null

    console.log('[创建患者] 准备发送的数据:', {
      hospitalId: hospitalId,
      patientId: patientId,
      name: patientForm.value.name,
      gender: patientForm.value.gender,
      birthDate: birthDateIso,
      phone: phoneDigits,
      idCard: patientForm.value.idCard?.replace(/\s/g, '') || null,
      department: department,
      attendingDoctorId: attendingDoctorId,
      chiefComplaint: patientForm.value.chiefComplaint?.trim(),
      heightCm: patientForm.value.heightCm ? parseInt(patientForm.value.heightCm) : null,
      weightKg: patientForm.value.weightKg ? parseFloat(patientForm.value.weightKg) : null,
      bloodType: patientForm.value.bloodType || 'Unknown',
      status: 'Active',
      allergies: patientForm.value.allergies?.trim(),
      medicalHistory: patientForm.value.medicalHistory?.trim(),
      familyHistory: patientForm.value.familyHistory?.trim(),
      firstVisitDate: patientForm.value.firstVisitDate ? formatDateTimeForBackend(patientForm.value.firstVisitDate) : null
    })

    // Prepare data for backend - map frontend fields to backend DTO fields
    // Ensure enum values match backend expectations
    const patientData = {
      hospitalId: hospitalId,
      patientId: patientId,
      name: patientForm.value.name.trim(),
      gender: patientForm.value.gender, // Should be 'M' or 'F'
      birthDate: birthDateIso,
      ...(phoneDigits ? { phone: phoneDigits } : {}),
      idCard: patientForm.value.idCard?.replace(/\s/g, '') || null,
      department: department?.trim(),
      attendingDoctorId: attendingDoctorId?.trim(),
      chiefComplaint: patientForm.value.chiefComplaint?.trim() || null,
      heightCm: patientForm.value.heightCm ? parseInt(patientForm.value.heightCm) : null,
      weightKg: patientForm.value.weightKg ? parseFloat(patientForm.value.weightKg) : null,
      bloodType: patientForm.value.bloodType || 'Unknown', // Should be 'A', 'B', 'O', 'AB', or 'Unknown'
      status: 'Active', // Should be 'Active', 'Discharged', or 'Deceased'
      allergies: patientForm.value.allergies?.trim() || null,
      medicalHistory: patientForm.value.medicalHistory?.trim() || null,
      familyHistory: patientForm.value.familyHistory?.trim() || null,
      firstVisitDate: patientForm.value.firstVisitDate ? formatDateTimeForBackend(patientForm.value.firstVisitDate) : null
    }

    if (showEditPatient.value) {
      await patientApi.updatePatient(selectedPatient.value.id, patientData)
    } else {
      await patientApi.createPatient(patientData)
    }
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || (showEditPatient.value ? '更新患者失败' : '新增患者失败')
  } finally {
    formLoading.value = false
  }
}

const handleStatusChange = async () => {
  formError.value = ''
  formLoading.value = true

  try {
    await patientApi.updateStatus(selectedPatient.value.id, newStatus.value)
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || '更改状态失败'
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async () => {
  formLoading.value = true

  try {
    await patientApi.deletePatient(selectedPatient.value.id)
    await fetchPatients()
    closeModals()
  } catch (err) {
    formError.value = err.message || '删除患者失败'
  } finally {
    formLoading.value = false
  }
}

// 处理新增患者
const handleAddPatient = async () => {
  // 加载最新的用户信息
  await loadCurrentUserInfo()
  // 打开新增患者对话框
  showAddPatient.value = true
}

// 获取当前用户的详细信息
const loadCurrentUserInfo = async () => {
  const currentUser = authStore.getCurrentUser()
  console.log('当前用户信息:', currentUser)

  if (currentUser) {
    // 填充ID
    patientForm.value.doctorId = currentUser.id || ''
    patientForm.value.attendingDoctorId = currentUser.id || ''  // Backend expects attendingDoctorId
    patientForm.value.attendingDoctorName = currentUser.fullName || currentUser.realName || currentUser.name || currentUser.userName || '医生'
    patientForm.value.hospitalId = currentUser.hospitalId || ''
    patientForm.value.department = currentUser.deptCode || ''  // Get department from user info

    // 为用户对象添加真实姓名字段（如果不存在）
    const userWithRealName = {
      ...currentUser,
      realName: currentUser.fullName || currentUser.realName || currentUser.name || currentUser.userName || '医生'
    }

    // 使用带真实姓名的用户信息
    currentDoctor.value = userWithRealName

    // 尝试获取医院信息
    if (currentUser.hospitalId) {
      try {
        const hospital = await authApi.getHospitalById(currentUser.hospitalId)
        console.log('获取的医院信息:', hospital)
        currentHospital.value = hospital
      } catch (err) {
        console.error('获取医院信息失败:', err)
        // 如果获取失败，创建一个简单的医院对象
        currentHospital.value = {
          id: currentUser.hospitalId,
          name: `医院 ${currentUser.hospitalId}`
        }
      }
    } else {
      // 如果没有医院ID，创建一个默认的医院对象
      currentHospital.value = {
        id: '',
        name: '未知医院'
      }
    }
  } else {
    console.error('未找到当前用户信息')
  }

  // 输出最终的状态
  console.log('patientForm:', patientForm.value)
  console.log('currentDoctor:', currentDoctor.value)
  console.log('currentHospital:', currentHospital.value)
}



// 关闭所有对话框
const closeModals = async () => {
  showAddPatient.value = false
  showEditPatient.value = false
  showViewPatient.value = false
  showChangeStatusModal.value = false
  showDeleteConfirm.value = false

  // 重置表单
  patientForm.value = {
    name: '',
    gender: 'M',  // Valid enum value for Gender.M
    birthDate: '',
    bloodType: 'Unknown',  // Valid enum value for BloodType.Unknown
    phone: '',
    idCard: '',
    doctorId: '',
    hospitalId: '',
    patientId: '',  // External patient ID
    department: '', // Department code
    attendingDoctorId: '', // Attending doctor ID
    // 新增字段
    chiefComplaint: '',
    heightCm: '',
    weightKg: '',
    allergies: '',
    medicalHistory: '',
    familyHistory: '',
    firstVisitDate: '',
  }

  // 重置详细信息
  currentHospital.value = null
  currentDoctor.value = null

  // 自动填充当前用户信息
  await loadCurrentUserInfo()

  selectedPatient.value = null
  newStatus.value = 'Active'
  formError.value = ''
}

// 格式化函数
const formatGender = (gender) => {
  const map = { M: '男', F: '女' }
  return map[gender] || gender || '-'
}

const formatBloodType = (bloodType) => {
  const map = { A: 'A型', B: 'B型', O: 'O型', AB: 'AB型', Unknown: '未知' }
  return map[bloodType] || bloodType || '-'
}

const formatStatus = (status) => {
  const map = { Active: '激活', Discharged: '出院', Deceased: '死亡' }
  return map[status] || status || '-'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

// 电话号码格式化和验证
const formatPhoneNumber = (phone) => {
  // 移除非数字字符
  const cleaned = phone.replace(/\D/g, '')
  // 检查是否为11位数字
  if (cleaned.length !== 11) return phone
  // 格式化为3-4-4空格间隔
  return cleaned.replace(/(\d{3})(\d{4})(\d{4})/, '$1 $2 $3')
}

const validatePhoneNumber = (phone) => {
  // 移除非数字字符
  const cleaned = phone.replace(/\D/g, '')
  // 验证是否为11位数字
  return /^1\d{10}$/.test(cleaned)
}

// 处理电话号码输入
const handlePhoneInput = () => {
  patientForm.value.phone = formatPhoneNumber(patientForm.value.phone)
}

// 身份证号格式化
const formatIdCard = (idCard) => {
  // 移除非数字和字母字符（保留X）
  const cleaned = idCard.replace(/[^0-9Xx]/g, '')
  // 检查长度
  if (cleaned.length !== 18 && cleaned.length !== 15) return idCard
  // 格式化为6-4-4-4空格间隔
  if (cleaned.length === 18) {
    return cleaned.replace(/(\d{6})(\d{4})(\d{4})([\dXx]{4})/, '$1 $2 $3 $4')
  } else {
    // 15位身份证号格式化为6-3-3-3
    return cleaned.replace(/(\d{6})(\d{3})(\d{3})(\d{3})/, '$1 $2 $3 $4')
  }
}

// 处理身份证号输入
const handleIdCardInput = () => {
  const idCard = patientForm.value.idCard
  // 格式化身份证号
  patientForm.value.idCard = formatIdCard(idCard)
  
  // 从身份证号提取出生日期
  const cleanedIdCard = idCard.replace(/[^0-9Xx]/g, '')
  if (cleanedIdCard.length === 18) {
    // 18位身份证号：第7-14位是出生日期，格式为YYYYMMDD
    const birthYear = cleanedIdCard.substring(6, 10)
    const birthMonth = cleanedIdCard.substring(10, 12)
    const birthDay = cleanedIdCard.substring(12, 14)
    patientForm.value.birthDate = `${birthYear}/${birthMonth}/${birthDay}`
  } else if (cleanedIdCard.length === 15) {
    // 15位身份证号：第7-12位是出生日期，格式为YYMMDD
    const birthYear = '19' + cleanedIdCard.substring(6, 8) // 假设是1900-1999年出生
    const birthMonth = cleanedIdCard.substring(8, 10)
    const birthDay = cleanedIdCard.substring(10, 12)
    patientForm.value.birthDate = `${birthYear}/${birthMonth}/${birthDay}`
  }
}



// Format birth date to ISO format (yyyy-mm-dd) for backend
const formatBirthDateForBackend = (dateStr) => {
  if (!dateStr) return dateStr
  // Convert yyyy/mm/dd to yyyy-mm-dd
  return dateStr.replace(/\//g, '-')
}

// Format datetime for backend (ISO 8601 format)
const formatDateTimeForBackend = (dateStr) => {
  if (!dateStr) return dateStr
  // datetime-local input returns format: yyyy-mm-ddThh:mm
  // Backend expects ISO format: yyyy-mm-ddThh:mm:ss
  if (dateStr.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)) {
    // If format is yyyy-mm-ddThh:mm, add seconds
    return dateStr + ':00'
  }
  // If already in correct format, return as is
  return dateStr
}

// Format datetime from backend for input (datetime-local format)
const formatDateTimeForInput = (dateStr) => {
  if (!dateStr) return ''
  // Backend returns ISO format: yyyy-mm-ddThh:mm:ss
  // datetime-local input expects: yyyy-mm-ddThh:mm
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

// 处理路由参数，用于从患者列表页面跳转到编辑页面
const handleRouteParams = async () => {
  const action = route.query.action
  const id = route.query.id
  
  if (action === 'edit' && id) {
    try {
      const patient = await patientApi.getPatientById(id)
      if (patient) {
        editPatient(patient)
      }
    } catch (err) {
      console.error('加载患者数据失败:', err)
      error.value = `加载患者数据失败: ${err.message || '未知错误'}`
    } finally {
      // 清除路由参数，避免刷新页面时重复触发
      if (route.query.action) {
        // 这里不直接修改路由，因为会导致页面跳转
        // 而是在编辑完成后由用户手动关闭
      }
    }
  }
}

// 监听路由参数变化
watch(() => route.query, () => {
  handleRouteParams()
}, { immediate: true })

onMounted(async () => {
  // 加载当前用户信息
  await loadCurrentUserInfo()
  // 加载患者列表
  fetchPatients()
  // 处理路由参数
  handleRouteParams()
})
</script>

<style scoped>
.patient-manage-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-height: 80vh;
}

.nav-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1.5rem 2rem;
}

.nav-bar h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.tab-content {
  padding: 0;
}

.toolbar {
  padding: 1.5rem;
  border-bottom: 1px solid #e0e0e0;
  background: #f9f9f9;
}

.toolbar h2 {
  margin: 0 0 1rem 0;
  color: #333;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  align-items: center;
}

.actions input,
.actions select {
  padding: 0.6rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
}

.actions input {
  width: 220px;
}

.actions select {
  min-width: 150px;
}

.actions input:focus,
.actions select:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.actions button {
  padding: 0.6rem 1.2rem;
  background: #f0f0f0;
  color: #333;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
}

.actions button:hover {
  background: #e0e0e0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-primary {
  background: #667eea !important;
  color: white !important;
}

.btn-primary:hover {
  background: #5568d3 !important;
}

.loading,
.error {
  text-align: center;
  padding: 4rem;
  color: #666;
  font-size: 1.1rem;
}

.error {
  color: #e74c3c;
}

.content {
  padding: 0;
}

.table-container {
  overflow-x: auto;
}

.patient-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}

.patient-table th,
.patient-table td {
  padding: 1rem 1.25rem;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.patient-table th {
  background: #f9f9f9;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.patient-table tr {
  transition: background-color 0.3s;
}

.patient-table tr:hover {
  background: #f5f5f5;
}

.patient-table .empty-state {
  padding: 4rem 2rem;
  text-align: center;
}

.patient-table .empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.patient-table .empty-icon {
  width: 60px;
  height: 60px;
  color: #d9d9d9;
}

.patient-table .empty-content p {
  margin: 0;
  color: #999;
  font-size: 0.95rem;
}

.patient-table .empty-content p:last-child {
  font-size: 0.85rem;
  color: #bbb;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge.Active {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-badge.Discharged {
  background: #fff3e0;
  color: #e65100;
}

.status-badge.Deceased {
  background: #ffebee;
  color: #c62828;
}

.patient-table .actions {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0.5rem 0;
}

.action-buttons {
  display: flex;
  gap: 0.3rem;
  flex-wrap: nowrap;
}

.btn-small {
  padding: 0.35rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 500;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 50px;
}

.btn-small:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.btn-view {
  background: #e3f2fd;
  color: #1976d2;
  border-color: #bbdefb;
}

.btn-view:hover {
  background: #bbdefb;
}

.btn-edit {
  background: #e8f5e9;
  color: #388e3c;
  border-color: #c8e6c9;
}

.btn-edit:hover {
  background: #c8e6c9;
}

.btn-status {
  background: #fff3e0;
  color: #f57c00;
  border-color: #ffe0b2;
}

.btn-status:hover {
  background: #ffe0b2;
}

.btn-delete {
  background: #ffebee;
  color: #d32f2f;
  border-color: #ffcdd2;
}

.btn-delete:hover {
  background: #ffcdd2;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  border-top: 1px solid #e0e0e0;
  background: #f9f9f9;
}

.pagination button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s;
}

.pagination button:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #667eea;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination span {
  color: #666;
  font-size: 0.9rem;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}

.modal {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  animation: modalFadeIn 0.3s ease-out;
}

.large-modal {
  max-width: 800px;
}

.modal h4 {
  margin: 1.5rem 0 1rem 0;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #667eea;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.modal h4:first-of-type {
  margin-top: 0;
}

.form-section {
  background: #f9f9f9;
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1rem;
}

.form-section .form-group {
  margin-bottom: 1rem;
}

@keyframes modalFadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal h3 {
  margin: 0 0 1.5rem 0;
  color: #333;
  font-size: 1.2rem;
  font-weight: 600;
}

.modal p {
  color: #666;
  margin-bottom: 1rem;
}

.form-group {
  margin-bottom: 1.2rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
  font-size: 0.95rem;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
  box-sizing: border-box;
  transition: border-color 0.3s;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

.form-group .info-display input {
  background-color: #f9f9f9;
  color: #333;
  cursor: default;
  border: 1px solid #e0e0e0;
}

.form-group .info-display input:focus {
  border-color: #e0e0e0;
  box-shadow: none;
}

/* 输入框带清除按钮样式 */
.input-with-clear {
  position: relative;
  display: flex;
  align-items: center;
}

.input-with-clear input,
.input-with-clear textarea {
  flex: 1;
  padding-right: 2.5rem;
}

.input-with-clear .clear-btn {
  position: absolute;
  right: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: #999;
  padding: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s;
}

.input-with-clear .clear-btn:hover {
  background: #f0f0f0;
  color: #666;
}

/* 文本域带清除按钮样式 */
.textarea-container {
  position: relative;
}

.textarea-container .clear-btn {
  top: 0.75rem;
  transform: none;
}

.error-message {
  color: #e74c3c;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.modal-actions {
  display: flex;
  gap: 1rem;
  margin-top: 2rem;
}

.modal-actions button {
  flex: 1;
  padding: 0.85rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
}

.modal-actions button[type="button"] {
  background: #f0f0f0;
  color: #666;
}

.modal-actions button[type="button"]:hover {
  background: #e0e0e0;
  transform: translateY(-1px);
}

.modal-actions button[type="submit"] {
  background: #667eea;
  color: white;
}

.modal-actions button[type="submit"]:hover:not(:disabled) {
  background: #5568d3;
  transform: translateY(-1px);
}

.modal-actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.patient-detail {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 0.75rem 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.3s;
}

.detail-row:hover {
  background: #f9f9f9;
}

.detail-row .label {
  color: #666;
  font-weight: 500;
  font-size: 0.95rem;
}

.detail-row .value {
  color: #333;
  text-align: right;
  font-size: 0.95rem;
  word-break: break-all;
}

.warning {
  color: #e74c3c;
  font-weight: 500;
  margin-top: 0.5rem;
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .actions input,
  .actions select,
  .actions button {
    width: 100%;
  }

  .patient-table th,
  .patient-table td {
    padding: 0.75rem;
    font-size: 0.9rem;
  }

  .patient-table .actions {
    flex-direction: column;
    align-items: stretch;
  }

  .patient-table .actions button {
    width: 100%;
    margin-bottom: 0.5rem;
  }

  .pagination {
    flex-wrap: wrap;
  }

  .modal {
    margin: 1rem;
    padding: 1.5rem;
  }
}
</style>
