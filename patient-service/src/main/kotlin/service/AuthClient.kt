package com.example.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import dto.UserDto
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.logging.*

/**
 * Auth 服务客户端
 * 用于调用 auth-service 的 API 进行用户验证和权限检查
 */
class AuthClient(
    private val baseUrl: String = "http://localhost:8081",
    private val token: String? = null
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    /**
     * 根据ID获取用户信息
     */
    suspend fun getUserInfo(userId: String, token: String? = null): UserDto.UserInfo? {
        return try {
            httpClient.get("$baseUrl/users/$userId") {
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 批量获取用户信息
     */
    suspend fun getUserInfos(userIds: List<String>, token: String? = null): List<UserDto.UserInfo> {
        return try {
            httpClient.post("$baseUrl/users/batch") {
                contentType(ContentType.Application.Json)
                setBody(UserIdsRequest(userIds))
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * 检查用户是否存在
     */
    suspend fun userExists(userId: String, token: String? = null): Boolean {
        return getUserInfo(userId, token) != null
    }

    /**
     * 检查用户是否为管理员
     */
    suspend fun isAdmin(userId: String, token: String? = null): Boolean {
        val user = getUserInfo(userId, token) ?: return false
        return user.role == "admin"
    }

    /**
     * 检查用户是否为医生
     */
    suspend fun isDoctor(userId: String, token: String? = null): Boolean {
        val user = getUserInfo(userId, token) ?: return false
        return user.role == "doctor"
    }

    /**
     * 获取用户角色
     */
    suspend fun getUserRole(userId: String, token: String? = null): String? {
        val user = getUserInfo(userId, token) ?: return null
        return user.role
    }

    /**
     * 获取用户的医院ID
     */
    suspend fun getUserHospitalId(userId: String, token: String? = null): String? {
        val user = getUserInfo(userId, token) ?: return null
        return user.hospitalId
    }

    /**
     * 获取用户的科室ID
     */
    suspend fun getUserDeptCode(userId: String): String? {
        val user = getUserInfo(userId) ?: return null
        return user.deptCode
    }

    /**
     * 检查用户是否可以访问患者数据
     * 规则：
     * 1. 管理员可以访问所有患者
     * 2. 医生可以访问自己负责的患者
     * 3. 同科室医生可以查看患者基本信息
     */
    suspend fun canAccessPatientData(
        requesterId: String,
        patientAttendingDoctorId: String,
        patientHospitalId: String,
        patientDepartment: String
    ): Boolean {
        val requester = getUserInfo(requesterId) ?: return false

        // 管理员可以访问所有患者数据
        if (requester.role == "admin") {
            return true
        }

        // 医生可以访问自己负责的患者
        if (requesterId == patientAttendingDoctorId) {
            return true
        }

        // 同科室医生可以访问患者基本信息
        if (requester.role == "doctor" &&
            requester.hospitalId == patientHospitalId &&
            requester.deptCode == patientDepartment) {
            return true
        }

        return false
    }

    /**
     * 检查用户是否可以修改患者
     * 规则：
     * 1. 管理员可以修改所有患者
     * 2. 前台/挂号人员可以修改
     * 3. 医生不可修改（仅查看）
     */
    suspend fun canModifyPatient(
        requesterId: String,
        patientAttendingDoctorId: String,
        token: String? = null
    ): Boolean {
        val requester = getUserInfo(requesterId, token) ?: return false

        // 管理员可以修改所有患者
        if (requester.role == "admin") {
            return true
        }

        // 前台/挂号人员可以修改患者
        if (requester.role == "receptionist") {
            return true
        }

        // 医生不可修改（只读）
        return false
    }

    /**
     * 检查用户是否可以创建患者
     * 规则：
     * 1. 管理员可以创建
     * 2. 前台/挂号人员可以创建
     * 3. 医生/护士不可创建
     */
    suspend fun canCreatePatient(
        requesterId: String,
        token: String? = null
    ): Boolean {
        val requester = getUserInfo(requesterId, token) ?: return false
        return requester.role == "admin" || requester.role == "receptionist"
    }

    /**
     * 验证医生ID是否有效且存在
     */
    suspend fun validateDoctor(doctorId: String, token: String? = null): Boolean {
        val user = getUserInfo(doctorId, token) ?: return false
        return user.role == "doctor" && !user.isDeleted && !user.isFrozen
    }

    /**
     * 获取科室医生列表
     */
    suspend fun getDepartmentDoctors(hospitalId: String, deptCode: String, token: String? = null): List<UserDto.UserInfo> {
        return try {
            httpClient.get("$baseUrl/users/doctors/$hospitalId/$deptCode") {
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }.body()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * 获取医生的真实姓名
     */
    suspend fun getDoctorName(doctorId: String, token: String? = null): String? {
        val user = getUserInfo(doctorId, token) ?: return doctorId // 如果获取失败，返回医生ID作为 fallback
        return user.fullName ?: user.username ?: doctorId
    }

    /**
     * 关闭 HTTP 客户端
     */
    fun close() {
        httpClient.close()
    }
}

@Serializable
data class UserIdsRequest(
    val userIds: List<String>
)
