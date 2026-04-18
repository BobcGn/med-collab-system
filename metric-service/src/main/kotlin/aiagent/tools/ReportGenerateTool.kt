package aiagent.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import dto.MetricDto
import dto.ReportDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.awt.image.BufferedImage
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * 正式报告生成工具。
 *
 * 该工具只允许消费已经过校验的结构化影像分析结果，
 * 不重新推理影像，不补做分析，不接受兼容模式输入直接生成正式报告。
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
    override val description = "仅根据已校验的结构化影像分析结果生成正式报告，不重新分析影像，不改写分析结论"

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
            val reportPreviewImage = buildReportPreviewImage(analysisPayload)
            val reportContent = renderReport(
                reportId = reportId,
                payload = analysisPayload,
                generatedAt = generatedAt,
                conclusion = conclusion,
                reportPreviewEmbedded = reportPreviewImage != null,
            )

            val reportPath = writeReportFile(
                reportId = reportId,
                patientId = analysisPayload.analysis.patientId,
                content = reportContent,
                reportPreviewImage = reportPreviewImage,
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
                highlightRegions = analysisPayload.highlightRegions,
                highlightLegend = analysisPayload.highlightLegend,
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
    }.getOrElse { error ->
        throw IllegalArgumentException(
            "正式报告仅接受经过校验的结构化影像分析结果，禁止使用兼容模式输入生成正式报告",
            error,
        )
    }
}

private fun buildConclusion(payload: MedicalImageAnalysisPayload): String {
    if (payload.analysisMode != "PIXEL") {
        return "本报告基于影像元数据生成，未读取到原始像素，结论仅用于流程联调和数据完整性确认。"
    }

    val highlightSuffix = payload.highlightRegions.firstOrNull()?.let { region ->
        "主病灶已以${region.colorName}${region.label}高亮标注。"
    } ?: "当前未生成彩色高亮区域。"

    return when (val metrics = payload.analysis.metrics) {
        is MetricDto.CTMetric ->
            "CT 指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的可疑区域，密度 ${metrics.density} HU-like，综合风险 ${metrics.severity}。$highlightSuffix"

        is MetricDto.MRIMetric ->
            "MRI 指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.signalIntensity} 区域，组织特征表现为 ${metrics.tissueCharacteristics}。$highlightSuffix"

        is MetricDto.XRayMetric ->
            "X 光指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.opacity}，骨结构评价为 ${metrics.boneStructure}。$highlightSuffix"

        is MetricDto.UltrasoundMetric ->
            "超声指标提示 ${metrics.location} 存在约 ${metrics.size} mm 的 ${metrics.echogenicity} 区域，血流表现为 ${metrics.bloodFlow}。$highlightSuffix"

        is MetricDto.GeneralMetric ->
            "当前影像已输出通用指标 ${metrics.name}，结果 ${metrics.value}${metrics.unit.orEmpty()}。$highlightSuffix"

        is MetricDto.MetricCollection -> {
            val firstMetric = metrics.metrics.firstOrNull() as? MetricDto.GeneralMetric
            if (firstMetric != null) {
                "当前影像已输出 ${metrics.metrics.size} 项通用指标，其中 ${firstMetric.name} 为 ${firstMetric.value}${firstMetric.unit.orEmpty()}。$highlightSuffix"
            } else {
                "当前影像已输出 ${metrics.metrics.size} 项结构化指标。$highlightSuffix"
            }
        }
    }
}

private fun renderReport(
    reportId: String,
    payload: MedicalImageAnalysisPayload,
    generatedAt: LocalDateTime,
    conclusion: String,
    reportPreviewEmbedded: Boolean,
): String {
    val sourceSection = buildSourceSection(payload.source)
    val indicatorSection = buildIndicatorsSection(payload.keyIndicators)
    val findingSection = buildBulletSection(payload.findings)
    val reportFigureSection = buildReportFigureSection(payload, reportPreviewEmbedded)
    val highlightSection = buildHighlightRegionsSection(payload.highlightRegions)
    val highlightLegendSection = buildHighlightLegendSection(payload.highlightLegend)
    val recommendationSection = buildNumberedSection(payload.recommendations)
    val detailSection = buildDetailSection(payload, reportPreviewEmbedded)
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
        |## 影像高亮附图
        |$reportFigureSection
        |
        |## 病灶高亮说明
        |$highlightSection
        |
        |## 高亮图例
        |$highlightLegendSection
        |
        |## 结论
        |$conclusion
        |
        |## 建议
        |$recommendationSection
        |
        |$limitationSection## 详细信息
        |$detailSection
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

private fun buildHighlightRegionsSection(regions: List<HighlightRegion>): String {
    if (regions.isEmpty()) {
        return "- 本次未生成需要声明的彩色高亮区域。"
    }

    val lines = mutableListOf(
        "- 共标记 ${regions.size} 个可疑区域，结果图中按优先级以 ${regions.joinToString("、") { "${it.colorName}${it.label}" }} 高亮。",
    )
    lines += regions.map { region ->
        "- ${region.colorName}${region.label}（${region.annotationTitle}）: ${region.annotationMeaning} 估计范围 ${region.estimatedSizeMm} mm，覆盖 ${region.coveragePercent}%，风险 ${region.severity}，置信度 ${round1(region.confidence * 100.0)}%。"
    }
    return lines.joinToString("\n")
}

private fun buildReportFigureSection(
    payload: MedicalImageAnalysisPayload,
    reportPreviewEmbedded: Boolean,
): String {
    if (payload.analysisMode != "PIXEL") {
        return buildBulletSection(
            listOf(
                "当前仅完成元数据级分析，未读取原始像素，因此报表不嵌入带标注影像。",
                "如需在报表内展示彩色高亮影像，请提供可读取像素的原始图片或 DICOM 栅格数据。",
            ),
        )
    }

    val lines = mutableListOf(
        if (reportPreviewEmbedded) {
            "报表已嵌入原始上传影像，并叠加轮廓高亮、标签和颜色说明。"
        } else {
            "当前未能嵌入原始上传影像，以下仍保留文字化高亮说明用于复核。"
        },
        "颜色与“高亮图例”保持一致，轮廓填充表示可疑范围，描边和标签用于定位与优先级提示。",
    )
    payload.highlightRegions.firstOrNull()?.let { region ->
        lines += "首要关注区域为 ${region.colorName}${region.label}，定位于 ${region.location}，说明为 ${region.annotationTitle}。"
    }
    return buildBulletSection(lines)
}

private fun buildHighlightLegendSection(legend: List<HighlightLegendItem>): String {
    if (legend.isEmpty()) {
        return "- 未生成颜色图例。"
    }

    return legend.joinToString("\n") { item ->
        "- [${item.colorHex}] ${item.colorName}: ${item.meaning}"
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

private fun buildDetailSection(
    payload: MedicalImageAnalysisPayload,
    reportPreviewEmbedded: Boolean,
): String {
    val confidenceValue = payload.keyIndicators.firstOrNull { it.name == "结果可信度" }?.value ?: "N/A"
    val lines = mutableListOf(
        "- 分析状态: ${payload.analysis.status}",
        "- 分析模式: ${payload.analysisMode}",
        "- 报表附图: ${if (reportPreviewEmbedded) "已嵌入带高亮原图" else "未嵌入带高亮原图"}",
        "- 原始像素: ${if (payload.source.rasterDataAvailable) "可读取" else "不可读取"}",
        "- 高亮区域数: ${payload.highlightRegions.size}",
        "- 首要高亮: ${payload.highlightRegions.firstOrNull()?.let { "${it.colorName}${it.label}" } ?: "未生成"}",
        "- 结果可信度: $confidenceValue${if (confidenceValue == "N/A") "" else "%"}",
        "- 影像来源类型: ${payload.source.sourceType}",
        "- 影像格式: ${payload.source.format ?: "N/A"}",
        "- MIME 类型: ${payload.source.mimeType ?: "N/A"}",
    )
    if (payload.source.width != null && payload.source.height != null) {
        lines += "- 图像尺寸: ${payload.source.width} x ${payload.source.height} px"
    }
    payload.source.fileSizeBytes?.let { lines += "- 文件大小: ${it} B" }
    return lines.joinToString("\n")
}

private fun round1(value: Double): Double {
    return (value * 10.0).roundToInt() / 10.0
}

private fun writeReportFile(
    reportId: String,
    patientId: String,
    content: String,
    reportPreviewImage: BufferedImage?,
): Path {
    return writeReportPdf(
        reportId = reportId,
        patientId = patientId,
        content = content,
        reportPreviewImage = reportPreviewImage,
    )
}

private fun buildReportPreviewImage(payload: MedicalImageAnalysisPayload): BufferedImage? {
    if (payload.analysisMode != "PIXEL") {
        return null
    }

    val baseImage = loadReportSourceImage(payload.imagePath) ?: return null
    return buildReportHighlightPreview(
        baseImage = baseImage,
        regions = payload.highlightRegions,
        legend = payload.highlightLegend,
    )
}

private fun loadReportSourceImage(reference: String): BufferedImage? {
    val normalized = reference.trim().removeSurrounding("\"")
    if (normalized.isBlank() || normalized.startsWith("inline-image://", ignoreCase = true)) {
        return null
    }

    if (normalized.startsWith("data:", ignoreCase = true)) {
        return runCatching { decodeReportDataUrl(normalized) }.getOrNull()
    }

    val path = runCatching { resolveReportSourcePath(normalized) }.getOrNull() ?: return null
    if (!Files.exists(path) || !Files.isReadable(path)) {
        return null
    }

    return runCatching {
        Files.newInputStream(path).use { ImageIO.read(it) }
    }.getOrNull()
}

private fun decodeReportDataUrl(reference: String): BufferedImage? {
    val header = reference.substringBefore(',')
    val payload = reference.substringAfter(',', "")
    if (payload.isBlank()) {
        return null
    }

    val bytes = if (header.contains(";base64", ignoreCase = true)) {
        Base64.getDecoder().decode(payload)
    } else {
        URLDecoder.decode(payload, Charsets.UTF_8).toByteArray(Charsets.UTF_8)
    }
    return bytes.inputStream().use { ImageIO.read(it) }
}

private fun resolveReportSourcePath(reference: String): Path {
    return try {
        if (reference.startsWith("file://", ignoreCase = true)) {
            Path.of(URI(reference))
        } else {
            Path.of(reference)
        }
    } catch (error: InvalidPathException) {
        throw IllegalArgumentException("报表影像路径不合法: $reference", error)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("报表影像路径不合法: $reference", error)
    }
}
