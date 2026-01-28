package com.example.database.repository

import database.entity.DepartmentEntity
import database.table.Departments
import dto.DepartmentDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class DepartmentRepository {
    /**
     * 创建部门
     */
    suspend fun createDepartment(department: DepartmentDto.DepartmentCreate): String {
        return try {
            transaction {
                Departments.insert {
                    it[id] = department.id
                    it[hospitalId] = department.hospitalId
                    it[name] = department.name
                } get Departments.id
            }.value
        } catch (e: Exception) {
            throw Exception("创建部门失败: ${e.message}")
        }
    }

    /**
     * 根据ID查找部门
     */
    suspend fun findDepartmentById(id: String): DepartmentDto.DepartmentInfo? {
        return try {
            transaction {
                DepartmentEntity.findById(id)?.let { entity ->
                    DepartmentDto.DepartmentInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        name = entity.name,
                        isActive = entity.isActive,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找部门失败: ${e.message}")
        }
    }

    /**
     * 根据医院ID查找所有激活的部门
     */
    suspend fun findActiveDepartmentsByHospitalId(hospitalId: String): List<DepartmentDto.DepartmentInfo> {
        return try {
            transaction {
                DepartmentEntity.find {
                    (Departments.hospitalId eq hospitalId) and (Departments.isActive eq true)
                }.map { entity ->
                    DepartmentDto.DepartmentInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        name = entity.name,
                        isActive = entity.isActive,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("获取部门列表失败: ${e.message}")
        }
    }

    /**
     * 根据医院ID查找所有部门（包括未激活状态）
     */
    suspend fun findAllDepartmentsByHospitalId(hospitalId: String): List<DepartmentDto.DepartmentInfo> {
        return try {
            transaction {
                DepartmentEntity.find { Departments.hospitalId eq hospitalId }
                    .map { entity ->
                        DepartmentDto.DepartmentInfo(
                            id = entity.id.value,
                            hospitalId = entity.hospitalId,
                            name = entity.name,
                            isActive = entity.isActive,
                            createdAt = entity.createdAt.toString(),
                            updatedAt = entity.updatedAt?.toString()
                        )
                    }
            }
        } catch (e: Exception) {
            throw Exception("获取部门列表失败: ${e.message}")
        }
    }

    /**
     * 更新部门信息
     */
    suspend fun updateDepartment(id: String, department: DepartmentDto.DepartmentUpdate): Boolean {
        return try {
            transaction {
                DepartmentEntity.findById(id)?.let { entity ->
                    department.name?.let { entity.name = it }
                    department.isActive?.let { entity.isActive = it }
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw Exception("更新部门失败: ${e.message}")
        }
    }

    /**
     * 删除部门（软删除）
     */
    suspend fun deleteDepartment(id: String): Boolean {
        return try {
            transaction {
                DepartmentEntity.findById(id)?.let { entity ->
                    entity.isActive = false
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw Exception("删除部门失败: ${e.message}")
        }
    }

    /**
     * 检查部门是否存在
     */
    suspend fun existsById(id: String): Boolean {
        return try {
            transaction {
                !DepartmentEntity.find { Departments.id eq id }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查部门是否存在失败: ${e.message}")
        }
    }

    /**
     * 检查部门是否存在且激活
     */
    suspend fun existsActiveById(id: String): Boolean {
        return try {
            transaction {
                !DepartmentEntity.find {
                    (Departments.id eq id) and (Departments.isActive eq true)
                }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查部门状态失败: ${e.message}")
        }
    }

    /**
     * 检查指定医院的部门是否存在
     */
    suspend fun existsByHospitalAndId(hospitalId: String, deptId: String): Boolean {
        return try {
            transaction {
                !DepartmentEntity.find {
                    (Departments.hospitalId eq hospitalId) and (Departments.id eq deptId)
                }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查部门是否存在失败: ${e.message}")
        }
    }

    /**
     * 检查指定医院的激活部门是否存在
     */
    suspend fun existsActiveByHospitalAndId(hospitalId: String, deptId: String): Boolean {
        return try {
            transaction {
                !DepartmentEntity.find {
                    (Departments.hospitalId eq hospitalId) and
                    (Departments.id eq deptId) and
                    (Departments.isActive eq true)
                }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查部门状态失败: ${e.message}")
        }
    }
}
