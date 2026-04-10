package com.example.database.repository

import database.entity.ReportEntity
import database.table.Reports
import dto.ReportDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/**
 * 报表仓库
 */
class ReportRepository {
    private val storageJson = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    /**
     * 新建报表
     */
    suspend fun createReport(report: ReportDto.ReportComplete): String {
        return try {
            val id = normalizeIdentifier(report.id)
            val normalizedAnalysisIds = report.analysisIds.map(::normalizeIdentifier)
            val persistedStatus = normalizeStatus(report.status, report.errorMessage)

            transaction {
                val existing = ReportEntity.findById(id)
                if (existing != null) {
                    existing.hospitalId = report.hospitalId
                    existing.patientId = report.patientId
                    existing.patientName = report.patientName
                    existing.analysisIds = storageJson.encodeToString(ListSerializer(String.serializer()), normalizedAnalysisIds)
                    existing.reportType = report.reportType
                    existing.filePath = report.filePath
                    existing.fileSize = report.fileSize
                    existing.status = persistedStatus
                    existing.errorMessage = report.errorMessage
                    existing.generatedAt = parseDateTime(report.generatedAt)
                    existing.isDeleted = false
                } else {
                    Reports.insert {
                        it[Reports.id] = id
                        it[Reports.hospitalId] = report.hospitalId
                        it[Reports.patientId] = report.patientId
                        it[Reports.patientName] = report.patientName
                        it[Reports.analysisIds] = storageJson.encodeToString(ListSerializer(String.serializer()), normalizedAnalysisIds)
                        it[Reports.reportType] = report.reportType
                        it[Reports.filePath] = report.filePath
                        it[Reports.fileSize] = report.fileSize
                        it[Reports.status] = persistedStatus
                        it[Reports.errorMessage] = report.errorMessage
                        it[Reports.generatedAt] = parseDateTime(report.generatedAt)
                        it[Reports.isDeleted] = false
                    }
                }
            }

            id
        } catch (e: Exception) {
            throw Exception("创建报表失败: ${e.message}")
        }
    }

    /**
     * 获取所有报表
     */
    suspend fun getAllReports(): List<ReportDto.ReportComplete> {
        return try {
            transaction {
                ReportEntity.all()
                    .filter { !it.isDeleted }
                    .sortedByDescending { it.generatedAt ?: it.createdAt }
                    .map(::toDto)
            }
        } catch (e: Exception) {
            throw Exception("获取报表失败: ${e.message}")
        }
    }

    /**
     * 获取指定患者的报表（根据姓名查找）
     * @param patientName 患者姓名
     */
    suspend fun getReportsByPatientName(patientName: String): List<ReportDto.ReportComplete> {
        return try {
            transaction {
                ReportEntity.find {
                    (Reports.patientName eq patientName) and (Reports.isDeleted eq false)
                }
                    .sortedByDescending { it.generatedAt ?: it.createdAt }
                    .map(::toDto)
            }
        } catch (e: Exception) {
            throw Exception("获取患者报表失败: ${e.message}")
        }
    }

    suspend fun getReportById(reportId: String): ReportDto.ReportComplete? {
        return try {
            transaction {
                ReportEntity.findById(reportId)
                    ?.takeIf { !it.isDeleted }
                    ?.let(::toDto)
            }
        } catch (e: Exception) {
            throw Exception("获取报表详情失败: ${e.message}")
        }
    }

    private fun toDto(entity: ReportEntity): ReportDto.ReportComplete {
        return ReportDto.ReportComplete(
            id = entity.id.value,
            hospitalId = entity.hospitalId,
            patientId = entity.patientId,
            patientName = entity.patientName,
            analysisIds = storageJson.decodeFromString(
                ListSerializer(String.serializer()),
                entity.analysisIds,
            ),
            reportType = entity.reportType,
            filePath = entity.filePath,
            fileSize = entity.fileSize,
            status = entity.status,
            createdAt = entity.createdAt.toString(),
            generatedAt = entity.generatedAt?.toString(),
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
            "failed", "error" -> "failed"
            "completed", "generated", "success" -> "completed"
            else -> "generating"
        }
    }

    private fun parseDateTime(value: String?): LocalDateTime? {
        return value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
    }
}
