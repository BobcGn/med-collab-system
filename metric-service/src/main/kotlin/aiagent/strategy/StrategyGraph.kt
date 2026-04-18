package aiagent.strategy

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import aiagent.tools.GeneratedReportPayload
import aiagent.tools.MedicalImageAnalysisPayload
import aiagent.tools.MedicalImageAnalyzerTool
import aiagent.tools.ReportGenerateTool
import aiagent.tools.ToolErrorResponse
import aiagent.tools.toolJson
import enums.ImageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

internal object ClinicalPipelineBoundaryPolicy {
    const val analysisAuthority = "UNET_OR_DETERMINISTIC_ANALYZER_ONLY"
    const val reportAuthority = "REPORT_TOOL_ONLY"
    const val llmAuthority = "TEXT_EXPLANATION_ONLY"
    const val prohibitedBehavior =
        "LLM_MUST_NOT_SEGMENT_DIAGNOSE_REWRITE_STRUCTURED_ANALYSIS_OR_AUTHOR_OFFICIAL_REPORT"
}

internal data class ClinicalAnalysisRequest(
    val imagePath: String,
    val imageType: ImageType,
    val hospitalId: String,
    val patientId: String,
    val patientName: String,
    val message: String? = null,
)

private data class ValidatedClinicalAnalysisRequest(
    val request: ClinicalAnalysisRequest,
    val acceptedAt: String,
    val analysisAuthority: String = ClinicalPipelineBoundaryPolicy.analysisAuthority,
    val reportAuthority: String = ClinicalPipelineBoundaryPolicy.reportAuthority,
    val llmAuthority: String = ClinicalPipelineBoundaryPolicy.llmAuthority,
    val prohibitedBehavior: String = ClinicalPipelineBoundaryPolicy.prohibitedBehavior,
)

private data class StructuredClinicalAnalysis(
    val request: ValidatedClinicalAnalysisRequest,
    val payload: MedicalImageAnalysisPayload,
)

private data class StructuredClinicalReport(
    val analysis: StructuredClinicalAnalysis,
    val payload: GeneratedReportPayload,
)

/**
 * 解析用户输入并转成受控的影像分析请求。
 * 这里仅提取结构化字段，不允许自然语言直接改写分析结论。
 */
internal fun parseInput(input: String): ClinicalAnalysisRequest {
    val extracted = mutableMapOf<String, String>()
    val jsonObject = runCatching {
        Json.parseToJsonElement(input).jsonObject
    }.getOrNull()

    if (jsonObject != null) {
        extracted["imagePath"] = jsonObject["imagePath"]?.jsonPrimitive?.contentOrNull
            ?: jsonObject["imageUrl"]?.jsonPrimitive?.contentOrNull
            ?: ""
        extracted["imageType"] = jsonObject["imageType"]?.jsonPrimitive?.contentOrNull.orEmpty()
        extracted["hospitalId"] = jsonObject["hospitalId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        extracted["patientId"] = jsonObject["patientId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        extracted["patientName"] = jsonObject["patientName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        extracted["message"] = jsonObject["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    } else {
        extracted["imagePath"] = parseLooseField(input, "imagePath")
        extracted["imageType"] = parseLooseField(input, "imageType")
        extracted["hospitalId"] = parseLooseField(input, "hospitalId")
        extracted["patientId"] = parseLooseField(input, "patientId")
        extracted["patientName"] = parseLooseField(input, "patientName")
        extracted["message"] = parseLooseField(input, "message")
    }

    return ClinicalAnalysisRequest(
        imagePath = requireNotBlank(extracted["imagePath"], "影像路径不能为空"),
        imageType = normalizeImageType(extracted["imageType"].orEmpty()),
        hospitalId = requireNotBlank(extracted["hospitalId"], "医院ID不能为空"),
        patientId = requireNotBlank(extracted["patientId"], "患者ID不能为空"),
        patientName = requireNotBlank(extracted["patientName"], "患者姓名不能为空"),
        message = extracted["message"]?.trim()?.takeIf { it.isNotEmpty() },
    )
}

internal suspend fun executeControlledMetricReportPipeline(input: String): String {
    val acceptedRequest = acceptClinicalAnalysisRequest(parseInput(input))
    val analysis = runDeterministicClinicalAnalysis(acceptedRequest)
    val report = generateControlledClinicalReport(analysis)
    return toolJson.encodeToString(GeneratedReportPayload.serializer(), report.payload)
}

private fun parseLooseField(input: String, fieldName: String): String {
    if (!input.contains(fieldName)) {
        return ""
    }

    val suffix = input.substringAfter("$fieldName:", "")
    return suffix.substringBefore('"').trim()
}

private fun normalizeImageType(rawType: String): ImageType {
    return when (rawType.trim().uppercase().replace("-", "").replace("_", "")) {
        "XRAY" -> ImageType.XRAY
        "CT" -> ImageType.CT
        "MRI" -> ImageType.MRI
        "ULTRASOUND" -> ImageType.ULTRASOUND
        "PATHOLOGY" -> ImageType.PATHOLOGY
        else -> ImageType.OTHER
    }
}

private fun requireNotBlank(value: String?, message: String): String {
    return value?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(message)
}

private fun acceptClinicalAnalysisRequest(request: ClinicalAnalysisRequest): ValidatedClinicalAnalysisRequest {
    require(request.imageType != ImageType.OTHER) {
        "当前仅支持明确模态的医疗影像分析请求，禁止在未知模态下生成正式分析结果"
    }

    return ValidatedClinicalAnalysisRequest(
        request = request,
        acceptedAt = LocalDateTime.now().toString(),
    )
}

private suspend fun runDeterministicClinicalAnalysis(
    request: ValidatedClinicalAnalysisRequest,
): StructuredClinicalAnalysis {
    val rawOutput = MedicalImageAnalyzerTool.execute(
        MedicalImageAnalyzerTool.Args(
            imagePath = request.request.imagePath,
            imageType = request.request.imageType,
            hospitalId = request.request.hospitalId,
            patientId = request.request.patientId,
            patientName = request.request.patientName,
        ),
    )

    return StructuredClinicalAnalysis(
        request = request,
        payload = validateStructuredAnalysisPayload(
            rawOutput = rawOutput,
            request = request,
        ),
    )
}

private fun validateStructuredAnalysisPayload(
    rawOutput: String,
    request: ValidatedClinicalAnalysisRequest,
): MedicalImageAnalysisPayload {
    decodeToolError(rawOutput, stage = "结构化影像分析")

    val payload = runCatching {
        toolJson.decodeFromString(MedicalImageAnalysisPayload.serializer(), rawOutput)
    }.getOrElse { error ->
        throw IllegalStateException("结构化影像分析输出不符合契约: ${error.message}", error)
    }

    require(payload.analysis.errorMessage.isNullOrBlank()) {
        "结构化影像分析结果包含错误信息，禁止进入正式报告阶段"
    }
    require(payload.analysis.hospitalId == request.request.hospitalId) {
        "分析结果中的医院ID与请求不一致"
    }
    require(payload.analysis.patientId == request.request.patientId) {
        "分析结果中的患者ID与请求不一致"
    }
    require(payload.analysis.patientName == request.request.patientName) {
        "分析结果中的患者姓名与请求不一致"
    }
    require(payload.imageType == request.request.imageType.name) {
        "分析结果中的影像类型与请求不一致"
    }
    require(payload.analysisMode == "PIXEL" || payload.analysisMode == "METADATA_ONLY") {
        "分析结果中的 analysisMode 不受支持"
    }
    require(payload.summary.isNotBlank()) {
        "分析结果缺少结构化摘要"
    }
    require(payload.findings.isNotEmpty()) {
        "分析结果缺少结构化 findings"
    }
    require(payload.source.readable) {
        "输入影像不可读，禁止生成正式结构化结果"
    }

    return payload
}

private suspend fun generateControlledClinicalReport(
    analysis: StructuredClinicalAnalysis,
): StructuredClinicalReport {
    val rawOutput = ReportGenerateTool.execute(
        ReportGenerateTool.Args(
            analysisResult = toolJson.encodeToString(MedicalImageAnalysisPayload.serializer(), analysis.payload),
        ),
    )

    return StructuredClinicalReport(
        analysis = analysis,
        payload = validateGeneratedReportPayload(
            rawOutput = rawOutput,
            analysis = analysis,
        ),
    )
}

private fun validateGeneratedReportPayload(
    rawOutput: String,
    analysis: StructuredClinicalAnalysis,
): GeneratedReportPayload {
    decodeToolError(rawOutput, stage = "正式报告生成")

    val payload = runCatching {
        toolJson.decodeFromString(GeneratedReportPayload.serializer(), rawOutput)
    }.getOrElse { error ->
        throw IllegalStateException("正式报告输出不符合契约: ${error.message}", error)
    }

    require(payload.analysis.id == analysis.payload.analysis.id) {
        "报告引用的分析ID与结构化分析结果不一致"
    }
    require(payload.report.analysisIds.contains(analysis.payload.analysis.id)) {
        "报告未绑定当前结构化分析ID"
    }
    require(payload.report.hospitalId == analysis.payload.analysis.hospitalId) {
        "报告中的医院ID与结构化分析结果不一致"
    }
    require(payload.report.patientId == analysis.payload.analysis.patientId) {
        "报告中的患者ID与结构化分析结果不一致"
    }
    require(payload.report.patientName == analysis.payload.analysis.patientName) {
        "报告中的患者姓名与结构化分析结果不一致"
    }
    require(payload.report.status == "generated") {
        "报告未处于 generated 状态，禁止作为正式报告输出"
    }
    require(payload.reportContent.isNotBlank()) {
        "报告正文为空"
    }

    return payload
}

private fun decodeToolError(rawOutput: String, stage: String) {
    val toolError = runCatching {
        toolJson.decodeFromString(ToolErrorResponse.serializer(), rawOutput)
    }.getOrNull() ?: return

    if (toolError.errorCode.isNotBlank()) {
        val detail = toolError.detail?.takeIf { it.isNotBlank() } ?: toolError.message
        throw IllegalStateException("${stage}失败: $detail")
    }
}

private fun persistStructuredClinicalReport(report: StructuredClinicalReport): String {
    transaction {
        println(
            "登记正式报告: analysisAuthority=${report.analysis.request.analysisAuthority}, " +
                "reportAuthority=${report.analysis.request.reportAuthority}, " +
                "llmAuthority=${report.analysis.request.llmAuthority}, " +
                "analysisId=${report.payload.analysisId}, reportId=${report.payload.report.id}"
        )
    }

    return toolJson.encodeToString(GeneratedReportPayload.serializer(), report.payload)
}

/**
 * 医疗影像正式分析与正式报告策略图。
 *
 * 这条策略图只允许：
 * 1. 使用确定性的结构化分析工具产出分析结果；
 * 2. 使用正式报告工具消费结构化分析结果产出报告；
 * 3. LLM 仅能在图外承担非诊断、非分割、非报告定稿的文本解释职责。
 */
val metricReportStrategy = strategy<String, String>("医疗影像正式分析与正式报告策略") {
    val intakeSubgraph by subgraph<String, ValidatedClinicalAnalysisRequest>("受控请求受理子图") {
        val parseRequestNode by node<String, ClinicalAnalysisRequest>("解析结构化请求") { input ->
            parseInput(input)
        }

        val validateBoundaryNode by node<ClinicalAnalysisRequest, ValidatedClinicalAnalysisRequest>("强制职责边界") {
            request ->
            acceptClinicalAnalysisRequest(request)
        }

        edge(nodeStart forwardTo parseRequestNode)
        edge(parseRequestNode forwardTo validateBoundaryNode)
        edge(validateBoundaryNode forwardTo nodeFinish)
    }

    val analysisSubgraph by subgraph<ValidatedClinicalAnalysisRequest, StructuredClinicalAnalysis>(
        name = "受控结构化分析子图",
        tools = listOf(MedicalImageAnalyzerTool),
    ) {
        val runDeterministicAnalysisNode by node<ValidatedClinicalAnalysisRequest, StructuredClinicalAnalysis>(
            "调用受控分析工具"
        ) { request ->
            runDeterministicClinicalAnalysis(request)
        }

        edge(nodeStart forwardTo runDeterministicAnalysisNode)
        edge(runDeterministicAnalysisNode forwardTo nodeFinish)
    }

    val reportSubgraph by subgraph<StructuredClinicalAnalysis, String>(
        name = "正式报告生成子图",
        tools = listOf(ReportGenerateTool),
    ) {
        val generateReportNode by node<StructuredClinicalAnalysis, StructuredClinicalReport>("生成正式报告") { analysis ->
            generateControlledClinicalReport(analysis)
        }

        val persistReportNode by node<StructuredClinicalReport, String>("登记正式报告") { report ->
            persistStructuredClinicalReport(report)
        }

        edge(nodeStart forwardTo generateReportNode)
        edge(generateReportNode forwardTo persistReportNode)
        edge(persistReportNode forwardTo nodeFinish)
    }

    nodeStart then
        intakeSubgraph then
        analysisSubgraph then
        reportSubgraph then
        nodeFinish
}
