package com.example.service

import com.example.database.repository.HospitalRepository
import com.example.database.repository.DepartmentRepository
import dto.HospitalDto
import dto.DepartmentDto
import exception.AuthException

class HospitalService(
    private val hospitalRepository: HospitalRepository,
    private val departmentRepository: DepartmentRepository,
    private val userService: UserService
) {
    /**
     * 创建医院（仅管理员）
     */
    suspend fun createHospital(operatorId: String, hospitalCreate: HospitalDto.HospitalCreate): String {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        // 验证医院ID是否已存在
        if (hospitalRepository.existsById(hospitalCreate.id)) {
            throw AuthException.HospitalIdInvalidException()
        }

        return hospitalRepository.createHospital(hospitalCreate)
    }

    /**
     * 更新医院信息（仅管理员）
     */
    suspend fun updateHospital(operatorId: String, hospitalId: String, hospitalUpdate: HospitalDto.HospitalUpdate): Boolean {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        return hospitalRepository.updateHospital(hospitalId, hospitalUpdate)
    }

    /**
     * 删除医院（仅管理员，软删除）
     */
    suspend fun deleteHospital(operatorId: String, hospitalId: String): Boolean {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        return hospitalRepository.deleteHospital(hospitalId)
    }

    /**
     * 创建科室（仅管理员）
     */
    suspend fun createDepartment(operatorId: String, departmentCreate: DepartmentDto.DepartmentCreate): String {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        // 验证医院是否存在且激活
        if (!hospitalRepository.existsActiveById(departmentCreate.hospitalId)) {
            throw AuthException.HospitalOrDepartmentIdInvalidException()
        }

        // 验证科室是否已存在
        if (departmentRepository.existsByHospitalAndId(departmentCreate.hospitalId, departmentCreate.id)) {
            throw AuthException.DepartmentIdInvalidException()
        }

        return departmentRepository.createDepartment(departmentCreate)
    }

    /**
     * 更新科室信息（仅管理员）
     */
    suspend fun updateDepartment(operatorId: String, departmentId: String, departmentUpdate: DepartmentDto.DepartmentUpdate): Boolean {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        return departmentRepository.updateDepartment(departmentId, departmentUpdate)
    }

    /**
     * 删除科室（仅管理员，软删除）
     */
    suspend fun deleteDepartment(operatorId: String, departmentId: String): Boolean {
        // 验证操作者是否为管理员
        if (!userService.isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        return departmentRepository.deleteDepartment(departmentId)
    }
}
