package aiagent.tools

import dto.AnalysisResultDto
import dto.MetricDto
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs

internal fun buildResearchSegmentationPayload(
    response: SegmentationServiceResponse,
    args: MedicalImageAnalyzerTool.Args,
    imagePath: String,
    source: ImageSourceSnapshot,
    now: LocalDateTime,
): MedicalImageAnalysisPayload {
    require(response.model.supportedImageTypes == listOf("ULTRASOUND") && args.imageType.name == "ULTRASOUND") {
        "课程模型仅支持 ULTRASOUND，禁止跨模态使用"
    }
    require(response.model.inChannels == 1 && response.model.outputClasses == 1) {
        "课程模型必须使用单通道二值分割契约"
    }
    require(response.model.bundleManifestSha256?.matches(Regex("[a-f0-9]{64}")) == true) {
        "科研模型缺少可追溯的模型包哈希"
    }
    val imageWidth = requireNotNull(source.width) { "缺少原始影像宽度" }
    val imageHeight = requireNotNull(source.height) { "缺少原始影像高度" }
    val imagePixels = imageWidth.toLong() * imageHeight
    require(imagePixels > 0) { "原始影像尺寸无效" }
    require(response.regions.isNotEmpty()) { "未返回可复核的目标区域" }
    require(response.regions.map { it.regionId }.distinct().size == response.regions.size) {
        "分割区域ID重复"
    }
    response.regions.forEach { region ->
        val pixels = requireNotNull(region.pixelCount) { "科研区域缺少像素面积" }
        val width = requireNotNull(region.widthPx) { "科研区域缺少像素宽度" }
        val height = requireNotNull(region.heightPx) { "科研区域缺少像素高度" }
        require(pixels > 0 && pixels <= imagePixels && width in 1..imageWidth && height in 1..imageHeight) {
            "科研区域像素几何量无效"
        }
        require(pixels.toLong() <= width.toLong() * height) { "区域面积超出外接框" }
        require(region.estimatedSizeMm == null) { "未标定的课程影像不得返回毫米测量" }
        require(region.confidence.isFinite() && region.confidence in 0.0..1.0) { "前景概率无效" }
        require(region.coveragePercent.isFinite() && region.coveragePercent in 0.0..100.0) {
            "覆盖率无效"
        }
        require(abs(region.coveragePercent - pixels.toDouble() / imagePixels * 100.0) <= 0.05) {
            "覆盖率与像素面积不一致"
        }
        val box = region.boundingBox
        require(listOf(box.leftPercent, box.topPercent, box.widthPercent, box.heightPercent)
            .all { it.isFinite() && it in 0.0..100.0 }) { "区域边界框无效" }
        require(box.widthPercent > 0 && box.heightPercent > 0 &&
            box.leftPercent + box.widthPercent <= 100.01 &&
            box.topPercent + box.heightPercent <= 100.01) { "区域边界框超出原图" }
        require(abs(box.widthPercent - width.toDouble() / imageWidth * 100.0) <= 0.05 &&
            abs(box.heightPercent - height.toDouble() / imageHeight * 100.0) <= 0.05) {
            "边界框百分比与像素尺寸不一致"
        }
        require(region.contour.all { point ->
            point.xPercent.isFinite() && point.yPercent.isFinite() &&
                point.xPercent in 0.0..100.0 && point.yPercent in 0.0..100.0
        }) { "区域轮廓坐标无效" }
    }
    val totalPixels = response.regions.sumOf { requireNotNull(it.pixelCount).toLong() }
    require(totalPixels <= imagePixels) { "总前景面积超出原图" }
    val meanProbability = response.regions.sumOf { it.confidence * requireNotNull(it.pixelCount) } / totalPixels
    val primary = response.regions.maxBy { requireNotNull(it.pixelCount) }
    val metrics = MetricDto.MetricCollection(
        listOf(
            researchMetric("分割区域数", response.regions.size.toDouble(), "count"),
            researchMetric("前景像素面积", totalPixels.toDouble(), "px²"),
            researchMetric("前景覆盖率", totalPixels.toDouble() / imagePixels * 100.0, "%"),
            researchMetric("主区域宽度", requireNotNull(primary.widthPx).toDouble(), "px"),
            researchMetric("主区域高度", requireNotNull(primary.heightPx).toDouble(), "px"),
            researchMetric("前景平均概率（未校准）", meanProbability * 100.0, "%"),
        ),
    )
    val colors = listOf("#DC2626" to "红色", "#F97316" to "橙色", "#0891B2" to "青色", "#7C3AED" to "紫色")
    val highlights = response.regions.sortedByDescending { it.pixelCount }.mapIndexed { index, region ->
        val (color, colorName) = colors[index % colors.size]
        HighlightRegion(
            id = region.regionId,
            label = "R${index + 1}",
            colorName = colorName,
            colorHex = color,
            priority = index + 1,
            annotationTitle = "科研分割目标 ${index + 1}",
            annotationMeaning = "颜色仅用于区分区域，不表示临床风险；位置为图像坐标，不是解剖定位。",
            location = region.location,
            severity = "",
            confidence = region.confidence,
            coveragePercent = region.coveragePercent,
            estimatedSizeMm = null,
            shape = "BLOB",
            rotationDegrees = 0.0,
            boundingBox = HighlightBoundingBox(
                region.boundingBox.leftPercent, region.boundingBox.topPercent,
                region.boundingBox.widthPercent, region.boundingBox.heightPercent,
            ),
            contour = region.contour.map { HighlightContourPoint(it.xPercent, it.yPercent) },
            note = "像素面积 ${region.pixelCount} px²；前景概率未经临床校准。",
            pixelCount = region.pixelCount,
            widthPx = region.widthPx,
            heightPx = region.heightPx,
        )
    }
    val summary = "科研超声分割完成：${response.regions.size} 个目标区域，共 $totalPixels px²；不作诊断或风险判断。"
    return MedicalImageAnalysisPayload(
        analysis = AnalysisResultDto.AnalysisResultComplete(
            id = "ANL-${UUID.randomUUID()}",
            hospitalId = args.hospitalId,
            imageId = "IMG-${UUID.nameUUIDFromBytes("$imagePath|${response.requestId}".toByteArray())}",
            patientId = args.patientId,
            patientName = args.patientName,
            metrics = metrics,
            status = "completed",
            createdAt = now.toString(),
            completedAt = now.toString(),
            errorMessage = null,
        ),
        imageType = args.imageType.name,
        imagePath = imagePath,
        analysisMode = "PIXEL",
        source = source,
        keyIndicators = metrics.metrics.filterIsInstance<MetricDto.GeneralMetric>().map {
            IndicatorItem(it.name, it.value.toString(), it.unit, it.referenceRange)
        } + listOf(
            IndicatorItem("模型用途", "research", interpretation = "课程科研模型，不具备临床有效性证明"),
            IndicatorItem("模型包 SHA-256", response.model.bundleManifestSha256.orEmpty()),
            IndicatorItem("分割模型", "${response.model.name} ${response.model.version}"),
            IndicatorItem("质量门禁", response.qualityGate.status, interpretation = response.qualityGate.reason),
        ),
        findings = listOf(
            summary,
            "区域和轮廓来自已加载权重的 UNet 与确定性后处理；通过质量门禁不等于临床验证。",
            "未提供物理标定，仅报告像素几何量；不输出血流、良恶性或临床风险。",
        ),
        summary = summary,
        recommendations = listOf("仅用于课程研究与人工复核；不得用于临床诊断。"),
        highlightRegions = highlights,
        highlightLegend = highlights.distinctBy { it.colorHex }.map {
            HighlightLegendItem(it.colorName, it.colorHex, "区域标识，不表示风险等级")
        },
        limitations = listOf(
            "无真实独立队列评测和临床资格证明，模型加载成功不等于分析正确。",
            "前景概率未经校准；图像坐标不代表解剖位置，像素尺寸不代表毫米。",
        ),
        intendedUse = "research",
    )
}

private fun researchMetric(name: String, value: Double, unit: String): MetricDto.GeneralMetric {
    return MetricDto.GeneralMetric(name, value, unit, "科研测量，无临床参考范围")
}
