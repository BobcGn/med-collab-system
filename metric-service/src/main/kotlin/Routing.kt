package com.example

import ai.koog.ktor.aiAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import io.ktor.server.auth.*
import com.example.database.repository.AnalysisRepository
import com.example.database.repository.MedicalImageRepository
import com.example.database.repository.ReportRepository
import com.example.service.MetricService
import aiagent.strategy.metricReportStrategy
import dto.AnalysisResultDto
import dto.MedicalImageDto
import dto.ReportDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.collections.mapOf

fun Application.configureRouting() {
    // 创建Repository和Service实例
    val analysisRepository = AnalysisRepository()
    val medicalImageRepository = MedicalImageRepository()
    val reportRepository = ReportRepository()
    val metricService = MetricService(analysisRepository, medicalImageRepository, reportRepository)

    // 路由配置
    routing {
        // 健康检查
        get("/health") {
            try {
                // 测试数据库连接
                val result = transaction {
                    exec("SELECT 1") {
                        it.next()
                        it.getInt(1)
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ok",
                    "service" to "metric-service",
                    "database" to "connected"
                ))
            } catch (e: Exception) {
                application.log.error("健康检查失败", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "status" to "error",
                    "service" to "metric-service",
                    "database" to "disconnected",
                    "error" to e.message
                ))
            }
        }

        // ==================== 医学影像 API ====================
        route("/images") {
            // 获取所有医学影像
            get { 
                try {
                    val images = metricService.getAllMedicalImages()
                    call.respond(HttpStatusCode.OK, images)
                } catch (e: Exception) {
                    application.log.error("获取医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 创建医学影像
            post { 
                try {
                    val image = call.receive<MedicalImageDto.MedicalImageCreate>()
                    val result = metricService.createMedicalImage(image)
                    call.respond(HttpStatusCode.Created, mapOf<String, Any>(
                        "id" to result
                    ))
                } catch (e: Exception) {
                    application.log.error("创建医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 根据患者姓名查询医学影像
            get("/patient/{name}") { 
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val images = metricService.getMedicalImagesByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, images)
                } catch (e: Exception) {
                    application.log.error("查询医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 删除医学影像
            delete("/{id}") { 
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("影像ID不能为空")
                    val result = metricService.deleteMedicalImage(id)
                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to result
                    ))
                } catch (e: Exception) {
                    application.log.error("删除医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }
        }

        // ==================== 分析结果 API ====================
        route("/analyses") {
            // 获取所有分析结果
            get { 
                try {
                    val results = metricService.getAllAnalysisResults()
                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    application.log.error("获取分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 创建分析结果
            post { 
                try {
                    val result = call.receive<AnalysisResultDto.AnalysisResultComplete>()
                    val createdResult = metricService.createAnalysisResult(result)
                    call.respond(HttpStatusCode.Created, mapOf<String, Any>(
                        "id" to createdResult
                    ))
                } catch (e: Exception) {
                    application.log.error("创建分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 根据患者姓名查询分析结果
            get("/patient/{name}") { 
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val results = metricService.getAnalysisResultsByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    application.log.error("查询分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }
        }

        // ==================== 报表 API ====================
        route("/reports") {
            // 获取所有报表
            get { 
                try {
                    val reports = metricService.getAllReports()
                    call.respond(HttpStatusCode.OK, reports)
                } catch (e: Exception) {
                    application.log.error("获取报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 创建报表
            post { 
                try {
                    val report = call.receive<ReportDto.ReportCreate>()
                    val createdReport = metricService.createReport(report)
                    call.respond(HttpStatusCode.Created, mapOf<String, Any>(
                        "id" to createdReport
                    ))
                } catch (e: Exception) {
                    application.log.error("创建报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }

            // 根据患者姓名查询报表
            get("/patient/{name}") { 
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val reports = metricService.getReportsByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, reports)
                } catch (e: Exception) {
                    application.log.error("查询报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf(
                        "error" to e.message
                    ))
                }
            }
        }

        // ==================== AI Agent API ====================
        authenticate("jwt") {
            route("/ai") {
                // 医疗影像分析与报表生成
                post("/analyze-and-report") {
                    try {
                        val userInput = call.receiveText()

                        // 调用 AI Agent 进行分析
                        val output = aiAgent(
                            strategy = metricReportStrategy,
                            model = DeepSeekModels.DeepSeekReasoner,
                            input = userInput
                        )

                        call.respond(HttpStatusCode.OK, mapOf(
                            "result" to output
                        ))
                    } catch (e: Exception) {
                        application.log.error("AI 分析失败", e)
                        call.respond(HttpStatusCode.InternalServerError, mapOf(
                            "error" to (e.message ?: "未知错误")
                        ))
                    }
                }
            }
        }
    }
}
