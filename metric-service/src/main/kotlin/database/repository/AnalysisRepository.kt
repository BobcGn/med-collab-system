package com.example.database.repository

import database.entity.AnalysisResultEntity
import database.table.AnalysisResults
import dto.AnalysisResultDto
import dto.MetricDto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/**
 * 分析结果仓库
 */
class AnalysisRepository {
    private val storageJson = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    /**
     * 新建分析结果
     */
    suspend fun createResult(result: AnalysisResultDto.AnalysisResultComplete): String {
        return try {
            val id = normalizeIdentifier(result.id)
            val imageId = normalizeIdentifier(result.imageId)
            val persistedStatus = normalizeStatus(result.status, result.errorMessage)
            transaction {
                val existing = AnalysisResultEntity.findById(id)
                if (existing != null) {
                    existing.hospitalId = result.hospitalId
                    existing.imageId = imageId
                    existing.patientId = result.patientId
                    existing.patientName = result.patientName
                    existing.metrics = storageJson.encodeToString(MetricDto.serializer(), result.metrics)
                    existing.status = persistedStatus
                    existing.errorMessage = result.errorMessage
                    existing.completedAt = parseDateTime(result.completedAt)
                    existing.isDeleted = false
                } else {
                    AnalysisResults.insert {
                        it[AnalysisResults.id] = id
                        it[AnalysisResults.hospitalId] = result.hospitalId
                        it[AnalysisResults.imageId] = imageId
                        it[AnalysisResults.patientId] = result.patientId
                        it[AnalysisResults.patientName] = result.patientName
                        it[AnalysisResults.metrics] = storageJson.encodeToString(MetricDto.serializer(), result.metrics)
                        it[AnalysisResults.status] = persistedStatus
                        it[AnalysisResults.errorMessage] = result.errorMessage
                        it[AnalysisResults.isDeleted] = false
                        it[AnalysisResults.completedAt] = parseDateTime(result.completedAt)
                    }
                }
            }
            id
        }catch (e: Exception){
            throw Exception("生成分析结果失败: ${e.message}")
        }
    }

    /**
     * 获取所有分析结果
     */
    suspend fun getAllResults(): List<AnalysisResultDto.AnalysisResultComplete> {
        return try {
            transaction {
                AnalysisResultEntity.all()
                    .filter { !it.isDeleted }
                    .sortedByDescending { it.completedAt ?: it.createdAt }
                    .map(::toDto)
            }
        } catch (e: Exception) {
            throw Exception("获取分析结果失败: ${e.message}")
        }
    }

    /**
     * 获取指定患者的分析结果
     * @param patientName
     */
    suspend fun getResultsByPatientName(patientName: String): List<AnalysisResultDto.AnalysisResultComplete> {
        return try {
            transaction {
                AnalysisResultEntity.find {
                    (AnalysisResults.patientName eq patientName) and (AnalysisResults.isDeleted eq false)
                }
                    .sortedByDescending { it.completedAt ?: it.createdAt }
                    .map(::toDto)
            }
        } catch (e: Exception) {
            throw Exception("获取患者分析结果失败: ${e.message}")
        }
    }

    private fun toDto(entity: AnalysisResultEntity): AnalysisResultDto.AnalysisResultComplete {
        return AnalysisResultDto.AnalysisResultComplete(
            id = entity.id.value,
            hospitalId = entity.hospitalId,
            imageId = entity.imageId,
            patientId = entity.patientId,
            patientName = entity.patientName,
            metrics = storageJson.decodeFromString(MetricDto.serializer(), entity.metrics),
            status = when (entity.status) {
                "success" -> "completed"
                else -> entity.status
            },
            createdAt = entity.createdAt.toString(),
            completedAt = entity.completedAt?.toString(),
            errorMessage = entity.errorMessage,
        )
    }

    private fun normalizeIdentifier(raw: String): String {
        val trimmed = raw.trim()
        val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        return uuidRegex.find(trimmed)?.value ?: trimmed.takeIf { it.length <= 36 } ?: UUID.randomUUID().toString()
    }

    private fun normalizeStatus(status: String, errorMessage: String?): String {
        if (!errorMessage.isNullOrBlank()) {
            return "failed"
        }

        return when (status.trim().lowercase()) {
            "pending" -> "pending"
            "running" -> "running"
            "failed" -> "failed"
            else -> "success"
        }
    }

    private fun parseDateTime(value: String?): LocalDateTime? {
        return value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    }
}
