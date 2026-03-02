package com.example.database.repository

import dto.ReportDto
import org.jetbrains.exposed.sql.statements.InsertStatement

/**
 * 报表仓库
 */
class ReportRepository {
    /**
     * 新建报表
     */
    suspend fun createReport(report: ReportDto.ReportCreate): InsertStatement<Number> {
        TODO("Not yet implemented")
    }

    /**
     * 获取所有报表
     */
    suspend fun getAllReports(): List<ReportDto.ReportComplete> {
        TODO()
    }

    /**
     * 获取指定患者的报表（根据姓名查找）
     * @param patientName 患者姓名
     */
    suspend fun getReportsByPatientName(patientName: String): List<ReportDto.ReportComplete> {
        TODO()
    }
}