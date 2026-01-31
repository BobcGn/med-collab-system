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
    private val baseUrl: String = "http://localhost:8081"
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
    suspend fun getUserInfo(userId: String): UserDto.UserInfo? {
        return try {
            httpClient.get("$baseUrl/api/users/$userId").body()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 批量获取用户信息
     */
    suspend fun getUserInfos(userIds: List<String>): List<UserDto.UserInfo> {
        return try {
            httpClient.post("$baseUrl/api/users/batch") {
                contentType(ContentType.Application.Json)
                setBody(UserIdsRequest(userIds))
            }.body()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * 检查用户是否存在
     */
    suspend fun userExists(userId: String): Boolean {
        return getUserInfo(userId) != null
    }

    /**
     * 检查用户是否为管理员
     */
    suspend fun isAdmin(userId: String): Boolean {
        val user = getUserInfo(userId) ?: return false
        return user.role == "admin"
    }

    /**
     * 检查用户是否为医生
     */
    suspend fun isDoctor(userId: String): Boolean {
        val user = getUserInfo(userId) ?: return false
        return user.role == "doctor"
    }

    /**
     * 获取用户角色
     */
    suspend fun getUserRole(userId: String): String? {
        val user = getUserInfo(userId) ?: return null
        return user.role
    }

    /**
     * 获取用户的医院ID
     */
    suspend fun getUserHospitalId(userId: String): String? {
        val user = getUserInfo(userId) ?: return null
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
     * 比查看更严格，只有主治医生或管理员可以修改
     */
    suspend fun canModifyPatient(
        requesterId: String,
        patientAttendingDoctorId: String
    ): Boolean {
        val requester = getUserInfo(requesterId) ?: return false

        // 管理员可以修改所有患者
        if (requester.role == "admin") {
            return true
        }

        // 只有主治医生可以修改患者
        return requesterId == patientAttendingDoctorId
    }

    /**
     * 验证医生ID是否有效且存在
     */
    suspend fun validateDoctor(doctorId: String): Boolean {
        val user = getUserInfo(doctorId) ?: return false
        return user.role == "doctor" && !user.isDeleted && !user.isFrozen
    }

    /**
     * 获取科室医生列表
     */
    suspend fun getDepartmentDoctors(hospitalId: String, deptCode: String): List<UserDto.UserInfo> {
        return try {
            httpClient.get("$baseUrl/api/users/department/$hospitalId/$deptCode/doctors").body()
        } catch (e: Exception) {
            return emptyList()
        }
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
