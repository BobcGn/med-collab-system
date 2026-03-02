package com.example.database.repository

import database.entity.MedicalImageEntity
import database.table.MedicalImages
import dto.MedicalImageDto
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter


/**
 * 医疗图像仓库
 * 提供医学影像的CRUD操作和统计功能
 */
class MedicalImageRepository {
    /**
     * 创建医学影像
     */
    suspend fun createImage(image: MedicalImageDto.MedicalImageCreate): InsertStatement<Number> {
        return try {
            transaction {
                val id = MedicalImages.generateId()
                MedicalImages.insert {
                    it[MedicalImages.id] = id
                    it[MedicalImages.hospitalId] = image.hospitalId
                    it[MedicalImages.patientId] = image.patientId
                    it[MedicalImages.imageType] = image.imageType
                    it[MedicalImages.filePath] = image.filePath
                    it[MedicalImages.fileSize] = image.fileSize
                    it[MedicalImages.status] = "uploaded"
                    it[MedicalImages.isDeleted] = false
                }
            }
        }catch (e: Exception){
            throw Exception("创建医学影像失败")
        }
    }

    /**
     * 根据患者名字查询对应的影像
     */
    suspend fun findImageByPatientName(patientName: String): List<MedicalImageDto.MedicalImageDetail?> {
        return listOf(
            try {
            transaction {
                MedicalImageEntity.find {
                    MedicalImages.patientName.eq(patientName)
                }.firstOrNull()?.let { entity ->
                    MedicalImageDto.MedicalImageDetail(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        patientId = entity.patientId,
                        patientName = entity.patientName,
                        imageType = entity.imageType,
                        filePath = entity.filePath,
                        fileSize = entity.fileSize,
                        uploadTime = entity.uploadTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        status = entity.status,
                        isDeleted = entity.isDeleted
                    )
                }
            }
        }catch (e: Exception){
            throw Exception("该患者的影像不存在")
        })
    }

    /**
     * 获取所有医学影像
     */
    suspend fun getAllImages(): List<MedicalImageDto.MedicalImageDetail> {
        return try {
            transaction {
                MedicalImageEntity.all().map { entity ->
                    MedicalImageDto.MedicalImageDetail(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        patientId = entity.patientId,
                        patientName = entity.patientName,
                        imageType = entity.imageType,
                        filePath = entity.filePath,
                        fileSize = entity.fileSize,
                        uploadTime = entity.uploadTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        status = entity.status
                    )
                }
            }
        }catch (e: Exception){
            throw Exception("获取所有医学影像列表失败")
        }
    }

    /**
     * 删除对应id的影像
     */
    suspend fun deleteImage(id: String): Boolean {
        return try {
            transaction {
                MedicalImageEntity.findById(id)?.delete()
            }
            true
        }catch (e: Exception){
            throw Exception("删除医学影像失败")
        }
    }

}