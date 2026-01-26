package com.example.database.repository

import database.entity.HospitalEntity
import database.table.Hospitals
import dto.HospitalDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class HospitalRepository {
    /**
     * 创建医院
     */
    suspend fun createHospital(hospital: HospitalDto.HospitalCreate): String {
        return try {
            transaction {
                Hospitals.insert {
                    it[id] = hospital.id
                    it[name] = hospital.name
                } get Hospitals.id
            }.value
        } catch (e: Exception) {
            throw Exception("创建医院失败: ${e.message}")
        }
    }

    /**
     * 根据ID查找医院
     */
    suspend fun findHospitalById(id: String): HospitalDto.HospitalInfo? {
        return try {
            transaction {
                HospitalEntity.findById(id)?.let { entity ->
                    HospitalDto.HospitalInfo(
                        id = entity.id.value,
                        name = entity.name,
                        isActive = entity.isActive,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找医院失败: ${e.message}")
        }
    }

    /**
     * 获取所有医院（仅返回激活状态）
     */
    suspend fun findAllActiveHospitals(): List<HospitalDto.HospitalInfo> {
        return try {
            transaction {
                HospitalEntity.find { Hospitals.isActive eq true }
                    .map { entity ->
                        HospitalDto.HospitalInfo(
                            id = entity.id.value,
                            name = entity.name,
                            isActive = entity.isActive,
                            createdAt = entity.createdAt.toString(),
                            updatedAt = entity.updatedAt?.toString()
                        )
                    }
            }
        } catch (e: Exception) {
            throw Exception("获取医院列表失败: ${e.message}")
        }
    }

    /**
     * 获取所有医院（包括未激活状态）
     */
    suspend fun findAllHospitals(): List<HospitalDto.HospitalInfo> {
        return try {
            transaction {
                HospitalEntity.all()
                    .map { entity ->
                        HospitalDto.HospitalInfo(
                            id = entity.id.value,
                            name = entity.name,
                            isActive = entity.isActive,
                            createdAt = entity.createdAt.toString(),
                            updatedAt = entity.updatedAt?.toString()
                        )
                    }
            }
        } catch (e: Exception) {
            throw Exception("获取医院列表失败: ${e.message}")
        }
    }

    /**
     * 更新医院信息
     */
    suspend fun updateHospital(hospital: HospitalDto.HospitalUpdate): Boolean {
        return try {
            transaction {
                HospitalEntity.findById(hospital.id)?.let { entity ->
                    hospital.name?.let { entity.name = it }
                    hospital.isActive?.let { entity.isActive = it }
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw Exception("更新医院失败: ${e.message}")
        }
    }

    /**
     * 删除医院（软删除）
     */
    suspend fun deleteHospital(id: String): Boolean {
        return try {
            transaction {
                HospitalEntity.findById(id)?.let { entity ->
                    entity.isActive = false
                    true
                } ?: false
            }
        } catch (e: Exception) {
            throw Exception("删除医院失败: ${e.message}")
        }
    }

    /**
     * 检查医院是否存在
     */
    suspend fun existsById(id: String): Boolean {
        return try {
            transaction {
                !HospitalEntity.find { Hospitals.id eq id }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查医院是否存在失败: ${e.message}")
        }
    }

    /**
     * 检查医院是否存在且激活
     */
    suspend fun existsActiveById(id: String): Boolean {
        return try {
            transaction {
                !HospitalEntity.find {
                    (Hospitals.id eq id) and (Hospitals.isActive eq true)
                }.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查医院状态失败: ${e.message}")
        }
    }
}
