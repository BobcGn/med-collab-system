package com.example.database.repository

import com.example.database.entity.PatientEntity
import database.table.Patients
import dto.PatientDto
import exception.PatientException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

class PatientRepository {
    /**
     * 创建新患者
     */
    suspend fun createPatient(patient: PatientDto.CreatePatient): String {
        return try {
            transaction {
                val patientId = Patients.generateId()
                Patients.insert {
                    it[id] = patientId
                    it[hospitalId] = patient.hospitalId
                    it[Patients.patientId] = patient.patientId
                    it[name] = patient.name
                    it[gender] = patient.gender.name
                    it[birthDate] = patient.birthDate?.let { parseLocalDate(it) }
                    it[phone] = patient.phone
                    it[idCard] = patient.idCard
                    it[department] = patient.department
                    it[attendingDoctorId] = patient.attendingDoctorId
                    it[allergies] = patient.allergies
                    it[medicalHistory] = patient.medicalHistory
                    it[familyHistory] = patient.familyHistory
                    it[chiefComplaint] = patient.chiefComplaint
                    it[heightCm] = patient.heightCm?.toShort()
                    it[weightKg] = patient.weightKg?.toBigDecimal()
                    it[bloodType] = patient.bloodType.name
                    it[status] = patient.status.name.lowercase()
                    it[firstVisitDate] = patient.firstVisitDate?.let { parseLocalDateTime(it) }
                    it[lastVisitDate] = patient.firstVisitDate?.let { parseLocalDateTime(it) }
                } get Patients.id
                patientId
            }
        } catch (e: Exception) {
            throw PatientException.CreatePatientFailedException(message = "创建患者时出错: ${e.message}")
        }
    }

    /**
     * 根据ID查找患者详情
     */
    suspend fun findPatientById(id: String): PatientDto.PatientInfo? {
        return try {
            transaction {
                PatientEntity.findById(id)?.let { entity ->
                    entity.toPatientInfo()
                }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 根据医院和病历号查找患者
     */
    suspend fun findPatientByHospitalAndPatientId(hospitalId: String, patientId: String): PatientDto.PatientInfo? {
        return try {
            transaction {
                PatientEntity.find {
                    (Patients.hospitalId eq hospitalId) and (Patients.patientId eq patientId) and (Patients.isDeleted eq false)
                }.firstOrNull()?.let { entity ->
                    entity.toPatientInfo()
                }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 检查患者是否存在
     */
    suspend fun existsByHospitalAndPatientId(hospitalId: String, patientId: String): Boolean {
        return try {
            transaction {
                !PatientEntity.find {
                    (Patients.hospitalId eq hospitalId) and (Patients.patientId eq patientId)
                }.empty()
            }
        } catch (e: Exception) {
            throw PatientException.PatientDataInvalidException(message = "检查患者是否存在时出错: ${e.message}")
        }
    }

    /**
     * 更新患者信息
     */
    suspend fun updatePatient(patientUpdate: PatientDto.UpdatePatient): Boolean {
        return try {
            transaction {
                PatientEntity.findById(patientUpdate.id)?.let { entity ->
                    patientUpdate.name?.let { entity.name = it }
                    patientUpdate.phone?.let { entity.phone = it }
                    patientUpdate.idCard?.let { entity.idCard = it }
                    patientUpdate.department?.let { entity.department = it }
                    patientUpdate.attendingDoctorId?.let { entity.attendingDoctorId = it }
                    patientUpdate.allergies?.let { entity.allergies = it }
                    patientUpdate.medicalHistory?.let { entity.medicalHistory = it }
                    patientUpdate.familyHistory?.let { entity.familyHistory = it }
                    patientUpdate.chiefComplaint?.let { entity.chiefComplaint = it }
                    patientUpdate.heightCm?.let { entity.heightCm = it.toShort() }
                    patientUpdate.weightKg?.let { entity.weightKg = it.toBigDecimal() }
                    patientUpdate.bloodType?.let { entity.bloodType = it.name }
                    patientUpdate.status?.let { entity.status = it.name.lowercase() }
                    patientUpdate.lastVisitDate?.let { entity.lastVisitDate = parseLocalDateTime(it) }
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw PatientException.UpdatePatientFailedException()
        }
    }

    /**
     * 软删除患者
     */
    suspend fun deletePatient(id: String): Boolean {
        return try {
            transaction {
                PatientEntity.findById(id)?.let { entity ->
                    entity.isDeleted = true
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw PatientException.DeletePatientFailedException()
        }
    }

    /**
     * 根据医院ID查询患者列表
     */
    suspend fun findPatientsByHospital(hospitalId: String, page: Int = 0, size: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                PatientEntity.find {
                    (Patients.hospitalId eq hospitalId) and (Patients.isDeleted eq false)
                }
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 根据科室查询患者列表
     */
    suspend fun findPatientsByDepartment(department: String, page: Int = 0, size: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                PatientEntity.find {
                    (Patients.department eq department) and (Patients.isDeleted eq false)
                }
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.DepartmentInvalidException()
        }
    }

    /**
     * 根据主治医生ID查询患者列表
     */
    suspend fun findPatientsByAttendingDoctor(attendingDoctorId: String, page: Int = 0, size: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                PatientEntity.find {
                    (Patients.attendingDoctorId eq attendingDoctorId) and (Patients.isDeleted eq false)
                }
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.DoctorIdInvalidException()
        }
    }

    /**
     * 搜索患者
     */
    suspend fun searchPatients(keyword: String, page: Int = 0, size: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                PatientEntity.find {
                    ((Patients.name like "%$keyword%") or
                    (Patients.patientId like "%$keyword%") or
                    (Patients.hospitalId like "%$keyword%")) and
                    (Patients.isDeleted eq false)
                }
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 获取最近就诊的患者
     */
    suspend fun getRecentPatients(days: Int = 7, limit: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                val recentDate = LocalDateTime.now().minusDays(days.toLong())
                PatientEntity.find {
                    (Patients.lastVisitDate greaterEq recentDate) and (Patients.isDeleted eq false)
                }
                    .orderBy(Patients.lastVisitDate to SortOrder.DESC)
                    .limit(limit)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 获取患者统计信息
     */
    suspend fun getPatientStatistics(hospitalId: String? = null): PatientDto.PatientStatistics {
        return try {
            transaction {
                val query = if (hospitalId != null) {
                    PatientEntity.find { (Patients.hospitalId eq hospitalId) and (Patients.isDeleted eq false) }
                } else {
                    PatientEntity.find { Patients.isDeleted eq false }
                }

                val total = query.count().toInt()
                val byStatus = query.groupBy { it.status }.mapKeys { it.key }.mapValues { it.value.size }
                val byDepartment = query.groupBy { it.department }.mapKeys { it.key }.mapValues { it.value.size }
                val byBloodType = query.groupBy { it.bloodType ?: "Unknown" }.mapKeys { it.key }.mapValues { it.value.size }
                val recentPatients = query
                    .orderBy(Patients.lastVisitDate to SortOrder.DESC)
                    .limit(10)
                    .map { it.toPatientListItem() }

                PatientDto.PatientStatistics(
                    total = total,
                    byStatus = byStatus,
                    byDepartment = byDepartment,
                    byBloodType = byBloodType,
                    recentPatients = recentPatients
                )
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 获取所有患者列表（分页）
     */
    suspend fun findAllPatients(page: Int = 0, size: Int = 20): List<PatientDto.PatientListItem> {
        return try {
            transaction {
                PatientEntity.find { Patients.isDeleted eq false }
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        entity.toPatientListItem()
                    }
            }
        } catch (e: Exception) {
            throw PatientException.PatientNotFoundException()
        }
    }

    /**
     * 计算患者年龄
     */
    private fun calculateAge(birthDate: LocalDate?): Int? {
        return if (birthDate != null) {
            Period.between(birthDate, LocalDate.now()).years
        } else {
            null
        }
    }

    /**
     * 解析LocalDate
     */
    private fun parseLocalDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr)
    }

    /**
     * 解析LocalDateTime
     */
    private fun parseLocalDateTime(dateStr: String): LocalDateTime {
        return LocalDateTime.parse(dateStr)
    }
}
