package com.example

import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import aiagent.strategy.metricReportStrategy
import aiagent.tools.GeneratedReportPayload
import aiagent.tools.MedicalImageAnalysisPayload
import aiagent.tools.MedicalImageAnalyzerTool
import aiagent.tools.ReportGenerateTool
import aiagent.validation.MetricAiConversationScope
import aiagent.validation.buildUnsupportedMetricAiPrompt
import aiagent.validation.determineMetricAiConversationScope
import com.example.database.repository.AnalysisRepository
import com.example.database.repository.MedicalImageRepository
import com.example.database.repository.ReportRepository
import com.example.service.MetricService
import dto.AnalysisResultDto
import dto.MedicalImageDto
import dto.MetricAiSocketEnvelope
import dto.MetricAiSocketRequest
import dto.ReportDto
import dto.SocketUserContext
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import enums.ImageType
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import utils.JwtUtil

private val socketJson = Json { ignoreUnknownKeys = true }
private val metricPayloadJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "kind"
}

internal fun Application.configureRouting(deepSeekSettings: DeepSeekSettings) {
    val analysisRepository = AnalysisRepository()
    val medicalImageRepository = MedicalImageRepository()
    val reportRepository = ReportRepository()
    val metricService = MetricService(analysisRepository, medicalImageRepository, reportRepository)
    val conversationHistoryStore = ConversationHistoryStore()
    val jwtUtil = JwtUtil(environment.config)

    routing {
        get("/health") {
            try {
                transaction {
                    exec("SELECT 1") {
                        it.next()
                        it.getInt(1)
                    }
                }
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "ok",
                        "service" to "metric-service",
                        "database" to "connected",
                    )
                )
            } catch (e: Exception) {
                application.log.error("健康检查失败", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "service" to "metric-service",
                        "database" to "disconnected",
                        "error" to e.message,
                    )
                )
            }
        }

        route("/images") {
            get {
                try {
                    val images = metricService.getAllMedicalImages()
                    call.respond(HttpStatusCode.OK, images)
                } catch (e: Exception) {
                    application.log.error("获取医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post {
                try {
                    val image = call.receive<MedicalImageDto.MedicalImageCreate>()
                    val result = metricService.createMedicalImage(image)
                    call.respond(HttpStatusCode.Created, mapOf<String, Any>("id" to result))
                } catch (e: Exception) {
                    application.log.error("创建医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/patient/{name}") {
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val images = metricService.getMedicalImagesByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, images)
                } catch (e: Exception) {
                    application.log.error("查询医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            delete("/{id}") {
                try {
                    val id = call.parameters["id"] ?: throw IllegalArgumentException("影像ID不能为空")
                    val result = metricService.deleteMedicalImage(id)
                    call.respond(HttpStatusCode.OK, mapOf("success" to result))
                } catch (e: Exception) {
                    application.log.error("删除医学影像失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }

        route("/analyses") {
            get {
                try {
                    val results = metricService.getAllAnalysisResults()
                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    application.log.error("获取分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post {
                try {
                    val result = decodeAnalysisResult(call.receiveText())
                    val createdResult = metricService.createAnalysisResult(result)
                    call.respond(HttpStatusCode.Created, mapOf("id" to createdResult))
                } catch (e: Exception) {
                    application.log.error("创建分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/patient/{name}") {
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val results = metricService.getAnalysisResultsByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    application.log.error("查询分析结果失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }

        route("/reports") {
            get {
                try {
                    val reports = metricService.getAllReports()
                    call.respond(HttpStatusCode.OK, reports)
                } catch (e: Exception) {
                    application.log.error("获取报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            post {
                try {
                    val report = decodeReport(call.receiveText())
                    val createdReport = metricService.createReport(report)
                    call.respond(HttpStatusCode.Created, mapOf("id" to createdReport))
                } catch (e: Exception) {
                    application.log.error("创建报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/patient/{name}") {
                try {
                    val patientName = call.parameters["name"] ?: throw IllegalArgumentException("患者姓名不能为空")
                    val reports = metricService.getReportsByPatientName(patientName)
                    call.respond(HttpStatusCode.OK, reports)
                } catch (e: Exception) {
                    application.log.error("查询报表失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            get("/{id}/file") {
                try {
                    val reportId = call.parameters["id"] ?: throw IllegalArgumentException("报表ID不能为空")
                    val report = metricService.getReportById(reportId)
                        ?: run {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "报表不存在"))
                            return@get
                        }
                    val filePath = report.filePath
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Path.of(it).toAbsolutePath().normalize() }
                        ?: run {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "报表文件不存在"))
                            return@get
                        }
                    val reportDirectory = resolveProjectSubdirectory("reports")
                    if (!isDescendantPath(filePath, reportDirectory)) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "无权访问该报表文件"))
                        return@get
                    }
                    if (!Files.exists(filePath)) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "报表文件不存在"))
                        return@get
                    }

                    val disposition = when (call.request.queryParameters["disposition"]?.lowercase()) {
                        "attachment" -> ContentDisposition.Attachment
                        else -> ContentDisposition.Inline
                    }
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        disposition.withParameter(
                            ContentDisposition.Parameters.FileName,
                            filePath.fileName.toString(),
                        ).toString(),
                    )
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                    call.respondFile(filePath.toFile())
                } catch (e: Exception) {
                    application.log.error("获取报表文件失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }

        route("/conversations") {
            get("/{conversationId}") {
                try {
                    val conversationId = call.parameters["conversationId"] ?: throw IllegalArgumentException("会话ID不能为空")
                    val hospitalId = call.request.queryParameters["hospitalId"].orEmpty().ifBlank { "unknown-hospital" }
                    val patientName = call.request.queryParameters["patientName"].orEmpty()
                    val history = conversationHistoryStore.loadConversation(conversationId, hospitalId)
                        ?: ConversationHistoryDocument(
                            conversationId = conversationId,
                            patientName = patientName,
                            hospitalId = hospitalId,
                            messages = emptyList(),
                            updatedAt = LocalDateTime.now().toString(),
                        )
                    call.respond(HttpStatusCode.OK, history)
                } catch (e: Exception) {
                    application.log.error("加载会话历史失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            put("/{conversationId}") {
                try {
                    val conversationId = call.parameters["conversationId"] ?: throw IllegalArgumentException("会话ID不能为空")
                    val request = call.receive<ConversationHistoryDocument>()
                    val savedHistory = conversationHistoryStore.saveConversation(
                        request.copy(
                            conversationId = conversationId,
                            updatedAt = LocalDateTime.now().toString(),
                        ),
                    )
                    call.respond(HttpStatusCode.OK, savedHistory)
                } catch (e: Exception) {
                    application.log.error("保存会话历史失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

            delete("/{conversationId}") {
                try {
                    val conversationId = call.parameters["conversationId"] ?: throw IllegalArgumentException("会话ID不能为空")
                    val hospitalId = call.request.queryParameters["hospitalId"].orEmpty().ifBlank { "unknown-hospital" }
                    val deleted = conversationHistoryStore.deleteConversation(conversationId, hospitalId)
                    call.respond(HttpStatusCode.OK, mapOf("deleted" to deleted))
                } catch (e: Exception) {
                    application.log.error("删除会话历史失败", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }

        route("/ai") {
            post("/analyze-and-report") {
                val token = call.extractToken()
                val socketUser = token?.let { buildSocketUser(jwtUtil, it) }
                if (socketUser == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "未授权访问"))
                    return@post
                }

                try {
                    val request = socketJson.decodeFromString<MetricAiSocketRequest>(call.receiveText())
                    val response = buildAiResponse(
                        request = request,
                        user = socketUser,
                        deepSeekSettings = deepSeekSettings,
                        onExecutionError = { error ->
                            application.log.error("HTTP AI agent execution failed", error)
                        },
                        executeAgent = { input ->
                            aiAgent(
                                strategy = metricReportStrategy,
                                model = DeepSeekModels.DeepSeekReasoner,
                                input = input,
                            )
                        },
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "result" to (response.analysisResult ?: response.message),
                            "message" to response.message,
                            "confidence" to response.confidence,
                        )
                    )
                } catch (e: Exception) {
                    application.log.error("AI 分析失败", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "未知错误"))
                    )
                }
            }
        }

        webSocket("/ws/ai-agent") {
            val token = call.extractToken()
            val socketUser = token?.let { buildSocketUser(jwtUtil, it) }
            if (socketUser == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                return@webSocket
            }

            sendSocketEnvelope(
                MetricAiSocketEnvelope(
                    type = "connected",
                    message = "ai-agent websocket connected",
                    createdAt = LocalDateTime.now().toString(),
                    user = socketUser,
                )
            )

            for (frame in incoming) {
                if (frame !is Frame.Text) {
                    continue
                }

                val rawText = frame.readText()
                val request = try {
                    socketJson.decodeFromString<MetricAiSocketRequest>(rawText)
                } catch (_: Exception) {
                    sendSocketEnvelope(
                        MetricAiSocketEnvelope(
                            type = "error",
                            message = "invalid websocket payload",
                            createdAt = LocalDateTime.now().toString(),
                            user = socketUser,
                        )
                    )
                    continue
                }

                sendSocketEnvelope(
                    MetricAiSocketEnvelope(
                        type = "processing",
                        requestId = request.requestId,
                        message = "ai-agent is processing your request",
                        createdAt = LocalDateTime.now().toString(),
                        user = socketUser,
                    )
                )

                val response = try {
                    buildAiResponse(
                        request = request,
                        user = socketUser,
                        deepSeekSettings = deepSeekSettings,
                        onExecutionError = { error ->
                            application.log.error("WebSocket AI agent execution failed", error)
                        },
                        executeAgent = { input -> runMetricAiAgent(input) },
                    )
                } catch (e: Exception) {
                    application.log.error("WebSocket AI 分析失败", e)
                    MetricAiSocketEnvelope(
                        type = "error",
                        requestId = request.requestId,
                        message = e.message ?: "AI 分析失败",
                        createdAt = LocalDateTime.now().toString(),
                        user = socketUser,
                    )
                }

                sendSocketEnvelope(response)
            }
        }
    }
}

private fun ApplicationCall.extractToken(): String? {
    val queryToken = request.queryParameters["token"]?.trim()
    if (!queryToken.isNullOrBlank()) {
        return queryToken
    }

    return request.headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeUnless { it.isBlank() }
}

private fun buildSocketUser(
    jwtUtil: JwtUtil,
    token: String,
): SocketUserContext? {
    if (!jwtUtil.validateToken(token)) {
        return null
    }

    val userId = jwtUtil.getUserIdFromToken(token) ?: return null
    val username = jwtUtil.getUsernameFromToken(token)
    return SocketUserContext(
        userId = userId,
        username = username,
        displayName = username ?: userId,
        role = jwtUtil.getClaimFromToken(token, "role", String::class.java),
    )
}

private suspend fun buildAiResponse(
    request: MetricAiSocketRequest,
    user: SocketUserContext,
    deepSeekSettings: DeepSeekSettings,
    onExecutionError: (Throwable) -> Unit = {},
    executeAgent: suspend (String) -> String,
): MetricAiSocketEnvelope {
    val normalizedRequest = request.normalizeFor(user)
    val hasImage = !normalizedRequest.imageData.isNullOrBlank()
    val hasMessage = !normalizedRequest.message.isNullOrBlank()

    require(hasImage || hasMessage) { "消息与图片不能同时为空" }

    val createdAt = LocalDateTime.now().toString()
    when (determineMetricAiConversationScope(normalizedRequest.message, hasImage)) {
        MetricAiConversationScope.UNSUPPORTED -> {
            return MetricAiSocketEnvelope(
                type = "ai_response",
                requestId = normalizedRequest.requestId,
                message = buildUnsupportedMetricAiPrompt(),
                createdAt = createdAt,
                user = user,
            )
        }

        MetricAiConversationScope.METRIC_DISCUSSION -> {
            return MetricAiSocketEnvelope(
                type = "ai_response",
                requestId = normalizedRequest.requestId,
                message = buildTextReply(normalizedRequest),
                createdAt = createdAt,
                user = user,
            )
        }

        MetricAiConversationScope.IMAGE_ANALYSIS -> Unit
    }

    val localExecution = runCatching {
        runLocalMetricPipeline(normalizedRequest)
    }

    if (localExecution.isSuccess) {
        return MetricAiSocketEnvelope(
            type = "ai_response",
            requestId = normalizedRequest.requestId,
            message = "影像分析完成",
            analysisResult = localExecution.getOrNull(),
            confidence = 95,
            createdAt = createdAt,
            user = user,
        )
    }

    if (!deepSeekSettings.isConfigured) {
        val error = localExecution.exceptionOrNull() ?: IllegalStateException(deepSeekSettings.unavailableReason())
        return buildUnavailableAiResponse(
            request = normalizedRequest,
            user = user,
            createdAt = createdAt,
            reason = error.message ?: deepSeekSettings.unavailableReason(),
        )
    }

    val agentInput = normalizedRequest.toAgentInput()
    val executionResult = runCatching { executeAgent(agentInput) }

    return executionResult.fold(
        onSuccess = { analysisResult ->
            MetricAiSocketEnvelope(
                type = "ai_response",
                requestId = normalizedRequest.requestId,
                message = "影像分析完成",
                analysisResult = analysisResult,
                confidence = 90,
                createdAt = createdAt,
                user = user,
            )
        },
        onFailure = { error ->
            onExecutionError(error)
            MetricAiSocketEnvelope(
                type = "ai_response",
                requestId = normalizedRequest.requestId,
                message = "AI 分析失败，已返回基础结果",
                analysisResult = buildImageFallbackResponse(normalizedRequest, error),
                confidence = 0,
                createdAt = createdAt,
                user = user,
            )
        },
    )
}

private fun MetricAiSocketRequest.normalizeFor(_user: SocketUserContext): MetricAiSocketRequest {
    val imageType = normalizeImageType(imageType)
    return copy(
        requestId = requestId ?: "req-${System.currentTimeMillis()}",
        type = type.trim().ifBlank { "chat" },
        message = message?.trim(),
        imageData = imageData?.trim(),
        imageType = imageType,
        patientId = patientId?.trim().takeUnless { it.isNullOrBlank() } ?: "unknown-patient",
        patientName = patientName?.trim().takeUnless { it.isNullOrBlank() } ?: "未知患者",
        hospitalId = hospitalId?.trim().takeUnless { it.isNullOrBlank() } ?: "unknown-hospital",
    )
}

private fun normalizeImageType(rawType: String?): String {
    return when (rawType?.trim()?.uppercase()?.replace("-", "")?.replace("_", "")) {
        "XRAY" -> "XRAY"
        "CT" -> "CT"
        "MRI" -> "MRI"
        "ULTRASOUND" -> "ULTRASOUND"
        "PATHOLOGY" -> "PATHOLOGY"
        else -> "OTHER"
    }
}

private fun MetricAiSocketRequest.toAgentInput(): String {
    return toAgentInput(socketJson)
}

private fun buildTextReply(request: MetricAiSocketRequest): String {
    val patientLabel = request.patientName ?: request.patientId ?: "当前患者"
    return buildString {
        append("已收到关于")
        append(patientLabel)
        append("的医疗影像/指标分析问题")
        if (!request.message.isNullOrBlank()) {
            append("：")
            append(request.message)
        }
        append("。当前可继续围绕影像所见、测量指标和报告内容交流；如需正式分析结果和报表，请继续上传医学影像。")
    }
}

private fun buildImageFallbackResponse(
    request: MetricAiSocketRequest,
    error: Throwable,
): String {
    return buildString {
        appendLine("影像分析结果")
        appendLine("患者: ${request.patientName} (${request.patientId})")
        appendLine("影像类型: ${request.imageType}")
        if (!request.message.isNullOrBlank()) {
            appendLine("补充说明: ${request.message}")
        }
        appendLine("AI 分析未成功完成，已返回基础结果。")
        append("建议: 请结合临床表现与原始影像进一步复核。")
        error.message?.takeIf { it.isNotBlank() }?.let {
            append(" 详细原因: ")
            append(it)
        }
    }
}

private fun buildUnavailableAiResponse(
    request: MetricAiSocketRequest,
    user: SocketUserContext,
    createdAt: String,
    reason: String,
): MetricAiSocketEnvelope {
    return MetricAiSocketEnvelope(
        type = "ai_response",
        requestId = request.requestId,
        message = "AI 服务未配置，已返回基础结果",
        analysisResult = buildImageFallbackResponse(
            request = request,
            error = IllegalStateException(reason),
        ),
        confidence = 0,
        createdAt = createdAt,
        user = user,
    )
}

private suspend fun runLocalMetricPipeline(request: MetricAiSocketRequest): String {
    val analysisResult = MedicalImageAnalyzerTool.execute(
        MedicalImageAnalyzerTool.Args(
            imagePath = request.imageData?.trim().orEmpty(),
            imageType = parseImageType(request.imageType),
            hospitalId = request.hospitalId.orEmpty(),
            patientId = request.patientId.orEmpty(),
            patientName = request.patientName.orEmpty(),
        ),
    )

    return ReportGenerateTool.execute(
        ReportGenerateTool.Args(
            analysisResult = analysisResult,
        ),
    )
}

private fun parseImageType(rawType: String?): ImageType {
    return when (rawType?.trim()?.uppercase()?.replace("-", "")?.replace("_", "")) {
        "XRAY" -> ImageType.XRAY
        "CT" -> ImageType.CT
        "MRI" -> ImageType.MRI
        "ULTRASOUND" -> ImageType.ULTRASOUND
        "PATHOLOGY" -> ImageType.PATHOLOGY
        else -> ImageType.OTHER
    }
}

private fun decodeAnalysisResult(rawBody: String): AnalysisResultDto.AnalysisResultComplete {
    runCatching {
        metricPayloadJson.decodeFromString(GeneratedReportPayload.serializer(), rawBody).analysis
    }.getOrNull()?.let { return it }

    runCatching {
        metricPayloadJson.decodeFromString(MedicalImageAnalysisPayload.serializer(), rawBody).analysis
    }.getOrNull()?.let { return it }

    return metricPayloadJson.decodeFromString(AnalysisResultDto.AnalysisResultComplete.serializer(), rawBody)
}

private fun decodeReport(rawBody: String): ReportDto.ReportComplete {
    runCatching {
        metricPayloadJson.decodeFromString(GeneratedReportPayload.serializer(), rawBody).report
    }.getOrNull()?.let { return it }

    runCatching {
        metricPayloadJson.decodeFromString(ReportDto.ReportComplete.serializer(), rawBody)
    }.getOrNull()?.let { return it }

    val createRequest = metricPayloadJson.decodeFromString(ReportDto.ReportCreate.serializer(), rawBody)
    val now = LocalDateTime.now().toString()
    return ReportDto.ReportComplete(
        id = UUID.randomUUID().toString(),
        hospitalId = createRequest.hospitalId,
        patientId = createRequest.patientId,
        patientName = createRequest.patientName,
        analysisIds = createRequest.analysisIds,
        reportType = createRequest.reportType,
        filePath = null,
        fileSize = null,
        status = "generating",
        createdAt = now,
        generatedAt = null,
        errorMessage = null,
    )
}

private suspend fun DefaultWebSocketServerSession.sendSocketEnvelope(
    envelope: MetricAiSocketEnvelope,
) {
    send(Frame.Text(socketJson.encodeToString(envelope)))
}

private suspend fun DefaultWebSocketServerSession.runMetricAiAgent(input: String): String {
    val routingCall = call as? RoutingCall
        ?: throw IllegalStateException("WebSocket call is not a RoutingCall")
    return RoutingContext(routingCall).aiAgent(
        strategy = metricReportStrategy,
        model = DeepSeekModels.DeepSeekReasoner,
        input = input,
    )
}
