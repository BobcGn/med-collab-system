package aiagent.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import dto.AnalysisResultDto
import dto.MetricDto
import dto.ReportDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

/**
 * 报表生成工具
 * @Input 上一步处理得到的分析结果
 * @Output 结构化报表结果
 */
object ReportGenerateTool : Tool<ReportGenerateTool.Args, String>() {
    @Serializable
    data class Args(
        @property:LLMDescription("上一步处理得到的分析结果")
        val analysisResult: String,
    )

    override val argsSerializer = Args.serializer()
    override val resultSerializer = String.serializer()
    override val name = "报表生成工具"
    override val description = "根据影像分析指标和患者信息生成正式报表并落盘"

    public override suspend fun execute(args: Args): String {
        return runCatching {
            val rawAnalysis = args.analysisResult.trim()
            if (rawAnalysis.isEmpty()) {
                throw IllegalArgumentException("分析结果不能为空")
            }

            val analysisPayload = parseAnalysisPayload(rawAnalysis)
            val generatedAt = LocalDateTime.now()
            val reportId = "REP-${UUID.randomUUID()}"
            val conclusion = buildConclusion(analysisPayload)
            val reportContent = renderReport(
                reportId = reportId,
                payload = analysisPayload,
                generatedAt = generatedAt,
                conclusion = conclusion,
            )

            val reportPath = writeReportFile(
                reportId = reportId,
                patientId = analysisPayload.analysis.patientId,
                content = reportContent,
            )
            val fileSize = Files.size(reportPath)
            val report = ReportDto.ReportComplete(
                id = reportId,
                hospitalId = analysisPayload.analysis.hospitalId,
                patientId = analysisPayload.analysis.patientId,
                patientName = analysisPayload.analysis.patientName,
                analysisIds = listOf(analysisPayload.analysis.id),
                reportType = if (analysisPayload.analysisMode == "PIXEL") {
                    "${analysisPayload.imageType}_AI_REPORT"
                } else {
                    "${analysisPayload.imageType}_LIMITED_REPORT"
                },
                filePath = reportPath.toAbsolutePath().normalize().toString(),
                fileSize = fileSize,
                status = "generated",
                createdAt = generatedAt.toString(),
                generatedAt = generatedAt.toString(),
                errorMessage = null,
            )

            val payload = GeneratedReportPayload(
                analysis = analysisPayload.analysis,
                report = report,
                analysisId = analysisPayload.analysis.id,
                imageType = analysisPayload.imageType,
                analysisMode = analysisPayload.analysisMode,
                summary = analysisPayload.summary,
                keyIndicators = analysisPayload.keyIndicators,
                findings = analysisPayload.findings,
                conclusion = conclusion,
                recommendations = analysisPayload.recommendations,
                limitations = analysisPayload.limitations,
                reportContent = reportContent,
            )

            toolJson.encodeToString(GeneratedReportPayload.serializer(), payload)
        }.getOrElse { error ->
            toolJson.encodeToString(
                ToolErrorResponse.serializer(),
                ToolErrorResponse(
                    errorCode = "REPORT_GENERATION_FAILED",
                    message = "报表生成失败",
                    detail = error.message,
                ),
            )
        }
    }
}

private fun parseAnalysisPayload(rawAnalysis: String): MedicalImageAnalysisPayload {
    runCatching {
        toolJson.decodeFromString(ToolErrorResponse.serializer(), rawAnalysis)
    }.getOrNull()?.let { error ->
        if (error.errorCode.isNotBlank()) {
            throw IllegalArgumentException(error.detail ?: error.message)
        }
    }

    return runCatching {
        toolJson.decodeFromString(MedicalImageAnalysisPayload.serializer(), rawAnalysis)
    }.getOrElse {
        val analysis = toolJson.decodeFromString(
            AnalysisResultDto.AnalysisResultComplete.serializer(),
            rawAnalysis,
        )
        MedicalImageAnalysisPayload(
            analysis = analysis,
            imageType = inferImageType(analysis.metrics),
            imagePath = "",
            analysisMode = if (analysis.metrics is MetricDto.MetricCollection) {
                "METADATA_ONLY"
            } else {
                "PIXEL"
            },
            source = ImageSourceSnapshot(
                reference = "",
                resolvedPath = null,
                sourceType = "LEGACY_RESULT",
                format = null,
                mimeType = null,
                fileSizeBytes = null,
                width = null,
                height = null,
                readable = true,
                rasterDataAvailable = false,
            ),
            keyIndicators = legacyIndicators(analysis.metrics),
            findings = listOf("输入结果为兼容模式转换，已根据结构化指标补齐报表上下文。"),
            summary = "已根据结构化分析指标生成兼容报表。",
            recommendations = listOf("建议后续统一切换到新的结构化影像分析输出。"),
            limitations = listOf("当前输入不包含原始影像源信息。"),
        )
    }
}

private fun inferImageType(metrics: MetricDto): String {
    return when (metrics) {
        is MetricDto.CTMetric -> "CT"
        is MetricDto.MRIMetric -> "MRI"
        is MetricDto.XRayMetric -> "XRAY"
        is MetricDto.UltrasoundMetric -> "ULTRASOUND"
        is MetricDto.GeneralMetric, is MetricDto.MetricCollection -> "OTHER"
    }
}

private fun legacyIndicators(metrics: MetricDto): List<IndicatorItem> {
    return when (metrics) {
        is MetricDto.CTMetric -> listOf(
            IndicatorItem("密度", metrics.density.toString(), "HU-like", metrics.severity),
            IndicatorItem("异常范围", metrics.size.toString(), "mm"),
            IndicatorItem("异常位置", metrics.location),
        )

        is MetricDto.MRIMetric -> listOf(
            IndicatorItem("信号强度", metrics.signalIntensity),
            IndicatorItem("异常范围", metrics.size.toString(), "mm"),
            IndicatorItem("组织特征", metrics.tissueCharacteristics),
        )

        is MetricDto.XRayMetric -> listOf(
            IndicatorItem("密度表现", metrics.opacity),
            IndicatorItem("异常范围", metrics.size.toString(), "mm"),
            IndicatorItem("骨结构", metrics.boneStructure),
        )

        is MetricDto.UltrasoundMetric -> listOf(
            IndicatorItem("回声性", metrics.echogenicity),
            IndicatorItem("异常范围", metrics.size.toString(), "mm"),
            IndicatorItem("血流提示", metrics.bloodFlow),
        )

        is MetricDto.GeneralMetric -> listOf(
            IndicatorItem(metrics.name, metrics.value.toString(), metrics.unit, metrics.referenceRange)
        )

        is MetricDto.MetricCollection -> metrics.metrics.flatMap(::legacyIndicators)
    }
}

private fun buildConclusion(payload: MedicalImageAnalysisPayload): String {
    if (payload.analysisMode != "PIXEL") {
        return "本报告基于影像元数据生成，未读取到原始像素，结论仅用于流程联调和数据完整性确认。"
    }

    return when (val metrics = payload.analysis.metrics) {
        is MetricDto.CTMetric ->
            "CT 指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的可疑区域，密度 ${metrics.density} HU-like，综合风险 ${metrics.severity}。"

        is MetricDto.MRIMetric ->
            "MRI 指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.signalIntensity} 区域，组织特征表现为 ${metrics.tissueCharacteristics}。"

        is MetricDto.XRayMetric ->
            "X 光指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.opacity}，骨结构评价为 ${metrics.boneStructure}。"

        is MetricDto.UltrasoundMetric ->
            "超声指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.echogenicity} 区域，血流表现为 ${metrics.bloodFlow}。"

        is MetricDto.GeneralMetric ->
            "当前影像已输出通用指标 ${metrics.name}，结果 ${metrics.value}${metrics.unit.orEmpty()}。"

        is MetricDto.MetricCollection -> {
            val firstMetric = metrics.metrics.firstOrNull() as? MetricDto.GeneralMetric
            if (firstMetric != null) {
                "当前影像已输出 ${metrics.metrics.size} 项通用指标，其中 ${firstMetric.name} 为 ${firstMetric.value}${firstMetric.unit.orEmpty()}。"
            } else {
                "当前影像已输出 ${metrics.metrics.size} 项结构化指标。"
            }
        }
    }
}

private fun renderReport(
    reportId: String,
    payload: MedicalImageAnalysisPayload,
    generatedAt: LocalDateTime,
    conclusion: String,
): String {
    val sourceSection = buildSourceSection(payload.source)
    val indicatorSection = buildIndicatorsSection(payload.keyIndicators)
    val findingSection = buildBulletSection(payload.findings)
    val recommendationSection = buildNumberedSection(payload.recommendations)
    val limitationSection = if (payload.limitations.isEmpty()) {
        ""
    } else {
        """
        |## 局限性
        |${buildBulletSection(payload.limitations)}
        |
        """.trimMargin()
    }

    return """
        |# 医疗影像 AI 分析报告
        |
        |## 报告信息
        |- 报告编号: $reportId
        |- 分析编号: ${payload.analysis.id}
        |- 生成时间: $generatedAt
        |- 报告类型: ${if (payload.analysisMode == "PIXEL") "正式分析报告" else "受限分析报告"}
        |
        |## 患者信息
        |- 患者姓名: ${payload.analysis.patientName}
        |- 患者ID: ${payload.analysis.patientId}
        |- 医院ID: ${payload.analysis.hospitalId}
        |- 影像类型: ${payload.imageType}
        |
        |## 影像来源
        |$sourceSection
        |
        |## 分析摘要
        |${payload.summary}
        |
        |## 关键指标
        |$indicatorSection
        |
        |## 关键发现
        |$findingSection
        |
        |## 结论
        |$conclusion
        |
        |## 建议
        |$recommendationSection
        |
        |$limitationSection## 结构化状态
        |- 分析状态: ${payload.analysis.status}
        |- 分析模式: ${payload.analysisMode}
        |- 结果可信度: ${payload.keyIndicators.firstOrNull { it.name == "结果可信度" }?.value ?: "N/A"}%
        |
    """.trimMargin()
}

private fun buildSourceSection(source: ImageSourceSnapshot): String {
    val lines = mutableListOf(
        "- 来源类型: ${source.sourceType}",
        "- 原始引用: ${source.reference.ifBlank { "N/A" }}",
        "- 解析路径: ${source.resolvedPath ?: "N/A"}",
        "- 文件格式: ${source.format ?: "N/A"}",
        "- MIME 类型: ${source.mimeType ?: "N/A"}",
        "- 像素可用: ${if (source.rasterDataAvailable) "是" else "否"}",
    )
    source.fileSizeBytes?.let { lines += "- 文件大小: ${it} B" }
    if (source.width != null && source.height != null) {
        lines += "- 图像尺寸: ${source.width} x ${source.height} px"
    }
    return lines.joinToString("\n")
}

private fun buildIndicatorsSection(indicators: List<IndicatorItem>): String {
    if (indicators.isEmpty()) {
        return "- 无结构化指标"
    }

    return indicators.joinToString("\n") { indicator ->
        buildString {
            append("- ")
            append(indicator.name)
            append(": ")
            append(indicator.value)
            indicator.unit?.takeIf { it.isNotBlank() }?.let {
                append(" ")
                append(it)
            }
            indicator.interpretation?.takeIf { it.isNotBlank() }?.let {
                append(" (")
                append(it)
                append(')')
            }
        }
    }
}

private fun buildBulletSection(items: List<String>): String {
    if (items.isEmpty()) {
        return "- 无"
    }
    return items.joinToString("\n") { "- $it" }
}

private fun buildNumberedSection(items: List<String>): String {
    if (items.isEmpty()) {
        return "1. 无进一步建议"
    }
    return items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
}

private fun writeReportFile(reportId: String, patientId: String, content: String): Path {
    return writeReportPdf(
        reportId = reportId,
        patientId = patientId,
        content = content,
    )
}
