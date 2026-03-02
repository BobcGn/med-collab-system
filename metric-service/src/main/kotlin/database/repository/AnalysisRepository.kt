package com.example.database.repository

import database.table.AnalysisResults
import dto.AnalysisResultDto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * 分析结果仓库
 */
class AnalysisRepository {
    /**
     * 新建分析结果
     */
    suspend fun createResult(result: AnalysisResultDto.AnalysisResultComplete): InsertStatement<Number> {
        return try {
            transaction {
                val id = AnalysisResults.generateId()
                AnalysisResults.insert {
                    it[AnalysisResults.id] = id
                    it[AnalysisResults.hospitalId] = result.hospitalId
                    it[AnalysisResults.imageId] = result.imageId
                    it[AnalysisResults.patientId] = result.patientId
                    it[AnalysisResults.patientName] = result.patientName
                    it[AnalysisResults.metrics] = Json.encodeToString(result.metrics)
                    it[AnalysisResults.status] = "pending"
                    it[AnalysisResults.isDeleted] = false
                }
            }
        }catch (e: Exception){
            throw Exception("生成分析结果失败")
        }
    }

    /**
     * 获取所有分析结果
     */
    suspend fun getAllResults(): List<AnalysisResultDto.AnalysisResultComplete> {
        TODO()
    }

    /**
     * 获取指定患者的分析结果
     * @param patientName
     */
    suspend fun getResultsByPatientName(patientName: String): List<AnalysisResultDto.AnalysisResultComplete> {
        TODO()
    }
}