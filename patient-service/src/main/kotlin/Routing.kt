package com.example

import com.example.database.entity.PatientEntity
import com.example.database.repository.PatientRepository
import com.example.service.AuthClient
import com.example.service.PatientService
import dto.PatientDto
import exception.PatientException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureRouting() {
    // 创建Repository和Service实例
    val patientRepository = PatientRepository()
    val authClient = AuthClient()
    val patientService = PatientService(patientRepository, authClient)

    // 路由配置
    routing {
        // 健康检查
        get("/health") {
            try {
                // 测试数据库连接
                val result = transaction {
                    PatientEntity.all().count()
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ok",
                    "service" to "patient-service",
                    "database" to "connected",
                    "patientCount" to result
                ))
            } catch (e: Exception) {
                application.log.error("健康检查失败", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "status" to "error",
                    "service" to "patient-service",
                    "database" to "disconnected",
                    "error" to e.message
                ))
            }
        }

        // 需要认证的路由
        authenticate("jwt") {
            // ==================== 患者基本信息 API ====================

            // 创建患者
            post("/patients") {
                try {
                    application.log.info("开始接收创建患者请求")
                    val patientCreate = call.receive<PatientDto.CreatePatient>()
                    application.log.info("接收到的患者数据: $patientCreate")
                    // 从 JWT token 中获取认证令牌
                    val principal = call.principal<JWTPrincipal>()
                    val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                    val result = patientService.createPatient(patientCreate, token)
                    application.log.info("患者创建成功: ${result.id}")
                    call.respond(HttpStatusCode.Created, result)
                } catch (e: Exception) {
                    application.log.error("创建患者时发生异常", e)
                    throw e
                }
            }

            // 获取患者详情
            get("/patients/{id}") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patient = patientService.getPatientInfo(patientId, token)
                call.respond(HttpStatusCode.OK, patient)
            }

            // 根据医院和病历号获取患者
            get("/patients/hospital/{hospitalId}/{patientId}") {
                val hospitalId = call.parameters["hospitalId"] ?: throw PatientException.HospitalIdInvalidException()
                val patientId = call.parameters["patientId"] ?: throw PatientException.PatientNotFoundException()
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patient = patientService.getPatientByHospitalAndPatientId(hospitalId, patientId, token)
                call.respond(HttpStatusCode.OK, patient)
            }

            // 更新患者信息
            put("/patients/{id}") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val principal = call.principal<JWTPrincipal>()
                val operatorId = principal?.payload?.subject ?: throw Exception("Token中缺少用户ID")
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patientUpdate = call.receive<PatientDto.UpdatePatient>()
                val success = patientService.updatePatient(patientId, patientUpdate, operatorId, token)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 删除患者（软删除）
            delete("/patients/{id}") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val success = patientService.deletePatient(patientId)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 获取患者列表（分页，支持多条件查询）
            get("/patients") {
                try {
                    val hospitalId = call.request.queryParameters["hospitalId"]
                    val department = call.request.queryParameters["department"]
                    val attendingDoctorId = call.request.queryParameters["attendingDoctorId"]
                    val statusStr = call.request.queryParameters["status"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                    val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                    val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")

                    application.log.info("查询患者列表参数: hospitalId=$hospitalId, department=$department, attendingDoctorId=$attendingDoctorId, status=$statusStr, page=$page, size=$size")

                    val status = statusStr?.let { enumValueOf<enums.Status>(it) }

                    val patients = patientService.findPatientsByParams(
                        hospitalId = hospitalId,
                        department = department,
                        attendingDoctorId = attendingDoctorId,
                        status = status,
                        page = page,
                        size = size,
                        token = token
                    )

                    application.log.info("查询患者列表结果: ${patients.size} 条记录")
                    call.respond(HttpStatusCode.OK, patients)
                } catch (e: Exception) {
                    application.log.error("查询患者列表失败", e)
                    throw e
                }
            }

            // ==================== 患者查询 API ====================

            // 获取医院患者列表（分页）
            get("/patients/hospital/{hospitalId}") {
                val hospitalId = call.parameters["hospitalId"] ?: throw PatientException.HospitalIdInvalidException()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patients = patientService.getHospitalPatients(hospitalId, page, size, token)
                call.respond(HttpStatusCode.OK, patients)
            }

            // 获取科室患者列表（分页）
            get("/patients/department/{department}") {
                val department = call.parameters["department"] ?: throw PatientException.DepartmentInvalidException()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patients = patientService.getDepartmentPatients(department, page, size, token)
                call.respond(HttpStatusCode.OK, patients)
            }

            // 获取医生负责的患者列表（分页）
            get("/patients/doctor/{doctorId}") {
                val doctorId = call.parameters["doctorId"] ?: throw PatientException.DoctorIdInvalidException()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patients = patientService.getDoctorPatients(doctorId, page, size, token)
                call.respond(HttpStatusCode.OK, patients)
            }

            // 搜索患者
            get("/patients/search") {
                val keyword = call.request.queryParameters["keyword"] ?: throw PatientException.PatientDataInvalidException()
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patients = patientService.searchPatients(keyword, page, size, token)
                call.respond(HttpStatusCode.OK, patients)
            }

            // 获取最近就诊的患者
            get("/patients/recent") {
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val patients = patientService.getRecentPatients(days, limit, token)
                call.respond(HttpStatusCode.OK, patients)
            }

            // ==================== 患者状态管理 API ====================

            // 更新患者状态（出院、死亡等）
            put("/patients/{id}/status") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val principal = call.principal<JWTPrincipal>()
                val operatorId = principal?.payload?.subject ?: throw Exception("Token中缺少操作者ID")
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val request = call.receive<Map<String, String>>()
                val statusStr = request["status"] ?: throw PatientException.PatientDataInvalidException()
                val status = enumValueOf<enums.Status>(statusStr)
                val success = patientService.updatePatientStatus(patientId, status, operatorId, token)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 更新患者就诊信息
            put("/patients/{id}/visit") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val visitUpdate = call.receive<PatientVisitUpdate>()
                val success = patientService.updatePatientVisit(
                    patientId = patientId,
                    chiefComplaint = visitUpdate.chiefComplaint,
                    department = visitUpdate.department,
                    attendingDoctorId = visitUpdate.attendingDoctorId
                )
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 更新患者体征信息
            put("/patients/{id}/vitals") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val vitalsUpdate = call.receive<PatientVitalsUpdate>()
                val success = patientService.updatePatientVitals(
                    patientId = patientId,
                    heightCm = vitalsUpdate.heightCm,
                    weightKg = vitalsUpdate.weightKg,
                    bloodType = vitalsUpdate.bloodType
                )
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 更新患者医疗信息
            put("/patients/{id}/medical") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val medicalUpdate = call.receive<PatientMedicalUpdate>()
                val success = patientService.updatePatientMedicalInfo(
                    patientId = patientId,
                    allergies = medicalUpdate.allergies,
                    medicalHistory = medicalUpdate.medicalHistory,
                    familyHistory = medicalUpdate.familyHistory
                )
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // 转移患者（更换主治医生或科室）
            put("/patients/{id}/transfer") {
                val patientId = call.parameters["id"] ?: throw PatientException.PatientNotFoundException()
                val principal = call.principal<JWTPrincipal>()
                val operatorId = principal?.payload?.subject ?: throw Exception("Token中缺少操作者ID")
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val transferRequest = call.receive<PatientTransferRequest>()
                val success = patientService.transferPatient(
                    patientId = patientId,
                    newDepartment = transferRequest.newDepartment,
                    newAttendingDoctorId = transferRequest.newAttendingDoctorId,
                    operatorId = operatorId,
                    token = token
                )
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            }

            // ==================== 批量操作 API ====================

            // 批量删除患者
            delete("/patients/batch") {
                val patientIds = call.receive<List<String>>()
                val successCount = patientService.batchDeletePatients(patientIds)
                call.respond(HttpStatusCode.OK, mapOf("successCount" to successCount))
            }

            // ==================== 统计信息 API ====================

            // 获取患者统计信息
            get("/patients/statistics") {
                val hospitalId = call.request.queryParameters["hospitalId"]
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val statistics = patientService.getPatientStatistics(hospitalId, token)
                call.respond(HttpStatusCode.OK, statistics)
            }
        }
    }
}

// 辅助数据类
@kotlinx.serialization.Serializable
data class PatientVisitUpdate(
    val chiefComplaint: String? = null,
    val department: String? = null,
    val attendingDoctorId: String? = null
)

@kotlinx.serialization.Serializable
data class PatientVitalsUpdate(
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val bloodType: enums.BloodType? = null
)

@kotlinx.serialization.Serializable
data class PatientMedicalUpdate(
    val allergies: String? = null,
    val medicalHistory: String? = null,
    val familyHistory: String? = null
)

@kotlinx.serialization.Serializable
data class PatientTransferRequest(
    val newDepartment: String,
    val newAttendingDoctorId: String
)
