package com.example.service

import com.example.database.repository.PatientRepository
import dto.PatientDto
import enums.Status
import exception.PatientException

class PatientService(
    private val patientRepository: PatientRepository,
    private val authClient: AuthClient
) {
    // ==================== 业务方法 ====================

    /**
     * 创建新患者
     *
     * 业务规则：
     * 1. 检查患者是否已存在（同一医院下病历号唯一）
     * 2. 验证医生ID是否有效
     * 3. 创建患者记录
     */
    suspend fun createPatient(patient: PatientDto.CreatePatient, token: String? = null): PatientDto.PatientInfo {
        // 检查患者是否已存在
        if (patientRepository.existsByHospitalAndPatientId(patient.hospitalId, patient.patientId)) {
            throw PatientException.PatientAlreadyExistsException()
        }

        // 验证医生ID是否有效（必须是医生角色且未被删除/冻结）
        if (!authClient.validateDoctor(patient.attendingDoctorId, token)) {
            throw PatientException.DoctorIdInvalidException()
        }

        // 验证医院ID是否有效
        if (authClient.getUserHospitalId(patient.attendingDoctorId, token) != patient.hospitalId) {
            throw PatientException.HospitalIdInvalidException()
        }

        // 创建患者
        val patientId = patientRepository.createPatient(patient)

        return patientRepository.findPatientById(patientId)
            ?: throw PatientException.CreatePatientFailedException()
    }

    /**
     * 获取患者详情
     */
    suspend fun getPatientInfo(patientId: String, token: String? = null): PatientDto.PatientInfo {
        val patient = patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()
        
        // 获取医生姓名
        val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
        
        // 填充医生姓名
        return patient.copy(attendingDoctorName = doctorName)
    }

    /**
     * 根据医院和病历号获取患者
     */
    suspend fun getPatientByHospitalAndPatientId(hospitalId: String, patientId: String, token: String? = null): PatientDto.PatientInfo {
        val patient = patientRepository.findPatientByHospitalAndPatientId(hospitalId, patientId)
            ?: throw PatientException.PatientNotFoundException()
        
        // 获取医生姓名
        val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
        
        // 填充医生姓名
        return patient.copy(attendingDoctorName = doctorName)
    }

    /**
     * 更新患者信息
     *
     * 业务规则：
     * 1. 验证患者是否存在
     * 2. 验证操作者是否有权限修改（主治医生或管理员）
     * 3. 更新患者信息
     */
    suspend fun updatePatient(
        patientId: String,
        patientUpdate: PatientDto.UpdatePatient,
        operatorId: String,
        token: String? = null
    ): Boolean {
        // 验证患者是否存在
        val existingPatient = patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        // 权限验证 - 确保操作者有权修改该患者
        if (!authClient.canModifyPatient(operatorId, existingPatient.attendingDoctorId, token)) {
            throw PatientException.PermissionDeniedException()
        }

        // 如果要修改主治医生，验证新医生ID是否有效
        patientUpdate.attendingDoctorId?.let { newDoctorId ->
            if (!authClient.validateDoctor(newDoctorId, token)) {
                throw PatientException.DoctorIdInvalidException()
            }
        }

        val updatedPatient = patientUpdate.copy(id = patientId)
        return patientRepository.updatePatient(updatedPatient)
    }

    /**
     * 删除患者（软删除）
     */
    suspend fun deletePatient(patientId: String): Boolean {
        // 验证患者是否存在
        patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        return patientRepository.deletePatient(patientId)
    }

    /**
     * 获取医院患者列表（分页）
     */
    suspend fun getHospitalPatients(hospitalId: String, page: Int = 0, size: Int = 20, token: String? = null): List<PatientDto.PatientListItem> {
        val patients = patientRepository.findPatientsByHospital(hospitalId, page, size)
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }

    /**
     * 获取科室患者列表（分页）
     */
    suspend fun getDepartmentPatients(department: String, page: Int = 0, size: Int = 20, token: String? = null): List<PatientDto.PatientListItem> {
        val patients = patientRepository.findPatientsByDepartment(department, page, size)
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }

    /**
     * 获取医生负责的患者列表（分页）
     */
    suspend fun getDoctorPatients(doctorId: String, page: Int = 0, size: Int = 20, token: String? = null): List<PatientDto.PatientListItem> {
        val patients = patientRepository.findPatientsByAttendingDoctor(doctorId, page, size)
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }

    /**
     * 搜索患者
     */
    suspend fun searchPatients(keyword: String, page: Int = 0, size: Int = 20, token: String? = null): List<PatientDto.PatientListItem> {
        val patients = patientRepository.searchPatients(keyword, page, size)
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }

    /**
     * 获取最近就诊的患者
     */
    suspend fun getRecentPatients(days: Int = 7, limit: Int = 20, token: String? = null): List<PatientDto.PatientListItem> {
        val patients = patientRepository.getRecentPatients(days, limit)
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }

    /**
     * 获取患者统计信息
     */
    suspend fun getPatientStatistics(hospitalId: String? = null, token: String? = null): PatientDto.PatientStatistics {
        val statistics = patientRepository.getPatientStatistics(hospitalId)
        
        // 为最近患者列表填充医生姓名
        val recentPatientsWithDoctorName = statistics.recentPatients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
        
        return statistics.copy(recentPatients = recentPatientsWithDoctorName)
    }

    // ==================== 患者状态管理 ====================

    /**
     * 更新患者状态（出院、死亡等）
     */
    suspend fun updatePatientStatus(patientId: String, status: Status, operatorId: String, token: String? = null): Boolean {
        // 验证患者是否存在
        val patient = patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        // 权限验证 - 只有主治医生或管理员可以修改患者状态
        if (!authClient.canModifyPatient(operatorId, patient.attendingDoctorId, token)) {
            throw PatientException.PermissionDeniedException()
        }

        return patientRepository.updatePatient(
            PatientDto.UpdatePatient(
                id = patientId,
                status = status
            )
        )
    }

    /**
     * 更新患者就诊信息（主诉、最近就诊时间等）
     */
    suspend fun updatePatientVisit(
        patientId: String,
        chiefComplaint: String? = null,
        department: String? = null,
        attendingDoctorId: String? = null
    ): Boolean {
        // 验证患者是否存在
        val patient = patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        val lastVisitDate = java.time.LocalDateTime.now().toString()

        return patientRepository.updatePatient(
            PatientDto.UpdatePatient(
                id = patientId,
                chiefComplaint = chiefComplaint,
                department = department,
                attendingDoctorId = attendingDoctorId,
                lastVisitDate = lastVisitDate
            )
        )
    }

    /**
     * 更新患者体征信息（身高、体重、血型等）
     */
    suspend fun updatePatientVitals(
        patientId: String,
        heightCm: Int? = null,
        weightKg: Float? = null,
        bloodType: enums.BloodType? = null
    ): Boolean {
        // 验证患者是否存在
        patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        return patientRepository.updatePatient(
            PatientDto.UpdatePatient(
                id = patientId,
                heightCm = heightCm,
                weightKg = weightKg,
                bloodType = bloodType
            )
        )
    }

    /**
     * 更新患者医疗信息（过敏史、病史等）
     */
    suspend fun updatePatientMedicalInfo(
        patientId: String,
        allergies: String? = null,
        medicalHistory: String? = null,
        familyHistory: String? = null
    ): Boolean {
        // 验证患者是否存在
        patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        return patientRepository.updatePatient(
            PatientDto.UpdatePatient(
                id = patientId,
                allergies = allergies,
                medicalHistory = medicalHistory,
                familyHistory = familyHistory
            )
        )
    }

    /**
     * 转移患者（更换主治医生或科室）
     */
    suspend fun transferPatient(
        patientId: String,
        newDepartment: String,
        newAttendingDoctorId: String,
        operatorId: String,
        token: String? = null
    ): Boolean {
        // 验证患者是否存在
        val patient = patientRepository.findPatientById(patientId)
            ?: throw PatientException.PatientNotFoundException()

        // 验证新医生ID是否有效（必须是医生角色且未被删除/冻结）
        if (!authClient.validateDoctor(newAttendingDoctorId, token)) {
            throw PatientException.DoctorIdInvalidException()
        }

        // 权限验证 - 只有主治医生或管理员可以转移患者
        if (!authClient.canModifyPatient(operatorId, patient.attendingDoctorId, token)) {
            throw PatientException.PermissionDeniedException()
        }

        return patientRepository.updatePatient(
            PatientDto.UpdatePatient(
                id = patientId,
                department = newDepartment,
                attendingDoctorId = newAttendingDoctorId
            )
        )
    }

    // ==================== 批量操作 ====================

    /**
     * 批量删除患者
     */
    suspend fun batchDeletePatients(patientIds: List<String>): Int {
        var successCount = 0
        for (id in patientIds) {
            try {
                if (deletePatient(id)) {
                    successCount++
                }
            } catch (e: Exception) {
                // 记录日志，但不中断批量操作
                continue
            }
        }
        return successCount
    }

    /**
     * 根据条件查询患者
     */
    suspend fun findPatientsByParams(
        hospitalId: String? = null,
        department: String? = null,
        attendingDoctorId: String? = null,
        status: Status? = null,
        page: Int = 0,
        size: Int = 20,
        token: String? = null
    ): List<PatientDto.PatientListItem> {
        // 根据参数调用不同的查询方法
        val patients = when {
            attendingDoctorId != null -> patientRepository.findPatientsByAttendingDoctor(attendingDoctorId, page, size)
            department != null -> patientRepository.findPatientsByDepartment(department, page, size)
            hospitalId != null -> patientRepository.findPatientsByHospital(hospitalId, page, size)
            else -> patientRepository.findAllPatients(page, size)
        }
        
        // 为患者列表填充医生姓名
        return patients.map { patient ->
            val doctorName = authClient.getDoctorName(patient.attendingDoctorId, token)
            patient.copy(attendingDoctorName = doctorName)
        }
    }
}
