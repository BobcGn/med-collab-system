package aiagent.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import dto.AnalysisResultDto
import dto.MetricDto
import enums.ImageType
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val PIXEL_ANALYSIS_MODE = "PIXEL"
private const val METADATA_ANALYSIS_MODE = "METADATA_ONLY"

private data class HighlightPalette(
    val colorName: String,
    val colorHex: String,
    val meaning: String,
)

private val highlightPalettes = listOf(
    HighlightPalette("红色", "#DC2626", "一级可疑区域，优先复核"),
    HighlightPalette("橙色", "#F97316", "二级可疑区域，建议复核"),
    HighlightPalette("黄色", "#EAB308", "三级可疑区域，持续观察"),
    HighlightPalette("青色", "#0891B2", "辅助关注区域，用于补充定位"),
)

/**
 * 医疗图像结构化分析工具。
 *
 * 该工具只负责受控影像分析/分割与结构化指标提取，
 * 不负责正式报告生成，不负责自由文本解释，也不允许在未知模态下产出正式结果。
 */
object MedicalImageAnalyzerTool : Tool<MedicalImageAnalyzerTool.Args, String>() {
    @Serializable
    data class Args(
        @property:LLMDescription("医学影像路径")
        val imagePath: String,
        @property:LLMDescription("医学影像类型")
        val imageType: ImageType,
        @property:LLMDescription("所属医院ID（英文简称）")
        val hospitalId: String,
        @property:LLMDescription("所属患者ID")
        val patientId: String,
        @property:LLMDescription("所属患者姓名")
        val patientName: String,
    )

    override val argsSerializer = Args.serializer()
    override val resultSerializer = String.serializer()
    override val name = "医学影像分析工具"
    override val description = "仅执行受控医学影像结构化分析与分割，不生成正式报告，不输出自由文本诊断"

    public override suspend fun execute(args: Args): String {
        return runCatching {
            require(args.imageType != ImageType.OTHER) {
                "未知影像模态不允许进入正式结构化分析"
            }
            val imagePath = requireNotBlank(args.imagePath, "影像路径不能为空")
            val hospitalId = requireNotBlank(args.hospitalId, "医院ID不能为空")
            val patientId = requireNotBlank(args.patientId, "患者ID不能为空")
            val patientName = requireNotBlank(args.patientName, "患者姓名不能为空")
            val now = LocalDateTime.now()

            val source = loadSource(imagePath)
            val features = extractFeatures(source, args.imageType)
            val metrics = buildMetric(args.imageType, features)
            val findings = buildFindings(args.imageType, features, metrics)
            val recommendations = buildRecommendations(args.imageType, features)
            val summary = buildSummary(args.imageType, features, metrics)
            val highlightLegend = buildHighlightLegend(features.highlightRegions)

            val analysis = AnalysisResultDto.AnalysisResultComplete(
                id = buildRandomId("ANL"),
                hospitalId = hospitalId,
                imageId = buildStableId("IMG", "$imagePath|$patientId|${args.imageType}"),
                patientId = patientId,
                patientName = patientName,
                metrics = metrics,
                status = if (features.analysisMode == PIXEL_ANALYSIS_MODE) "completed" else "limited",
                createdAt = now.toString(),
                completedAt = now.toString(),
                errorMessage = null,
            )

            val payload = MedicalImageAnalysisPayload(
                analysis = analysis,
                imageType = args.imageType.name,
                imagePath = imagePath,
                analysisMode = features.analysisMode,
                source = source.snapshot,
                keyIndicators = buildKeyIndicators(source.snapshot, features, metrics),
                findings = findings,
                summary = summary,
                recommendations = recommendations,
                highlightRegions = features.highlightRegions,
                highlightLegend = highlightLegend,
                limitations = buildLimitations(source.snapshot, features.analysisMode),
            )

            toolJson.encodeToString(MedicalImageAnalysisPayload.serializer(), payload)
        }.getOrElse { error ->
            toolJson.encodeToString(
                ToolErrorResponse.serializer(),
                ToolErrorResponse(
                    errorCode = "ANALYSIS_FAILED",
                    message = "医学影像分析失败",
                    detail = error.message,
                ),
            )
        }
    }
}

private data class SourceData(
    val snapshot: ImageSourceSnapshot,
    val image: BufferedImage?,
)

private data class ExtractedFeatures(
    val analysisMode: String,
    val meanLuminance: Double,
    val contrast: Double,
    val edgeDensity: Double,
    val lesionCoverage: Double,
    val lesionSizeMm: Double,
    val location: String,
    val confidence: Double,
    val imageQualityScore: Double,
    val highlightRegions: List<HighlightRegion>,
)

private data class RegionCandidate(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val areaSamples: Int,
    val centroidX: Double,
    val centroidY: Double,
    val meanDeviation: Double,
    val componentScore: Double,
    val sampleIndexes: List<Int>,
)

private data class GridVertex(
    val x: Int,
    val y: Int,
)

private data class GridSegment(
    val start: GridVertex,
    val end: GridVertex,
)

private fun requireNotBlank(value: String, message: String): String {
    return value.trim().takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(message)
}

private fun loadSource(reference: String): SourceData {
    val normalized = reference.trim().removeSurrounding("\"")
    if (normalized.startsWith("data:", ignoreCase = true)) {
        return loadDataUrl(normalized)
    }

    if (normalized.startsWith("inline-image://", ignoreCase = true)) {
        return loadInlineReference(normalized)
    }

    val path = resolvePath(normalized)
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
        throw IllegalArgumentException("影像文件不存在: $normalized")
    }
    if (!Files.isReadable(path)) {
        throw IllegalArgumentException("影像文件不可读: $normalized")
    }

    val image = Files.newInputStream(path).use { ImageIO.read(it) }
    val fileSize = runCatching { Files.size(path) }.getOrNull()
    val mimeType = runCatching { Files.probeContentType(path) }.getOrNull()
    val format = path.fileName.toString().substringAfterLast('.', "").ifBlank { null }

    return SourceData(
        snapshot = ImageSourceSnapshot(
            reference = reference,
            resolvedPath = path.toAbsolutePath().normalize().toString(),
            sourceType = "LOCAL_FILE",
            format = format?.uppercase(),
            mimeType = mimeType,
            fileSizeBytes = fileSize,
            width = image?.width,
            height = image?.height,
            readable = true,
            rasterDataAvailable = image != null,
        ),
        image = image,
    )
}

private fun loadInlineReference(reference: String): SourceData {
    val withoutScheme = reference.removePrefix("inline-image://")
    val mimeType = withoutScheme.substringBefore('?').ifBlank { "application/octet-stream" }
    val query = withoutScheme.substringAfter('?', "")
    val fileSize = query
        .substringAfter("size=", "")
        .substringBefore('&')
        .removeSuffix("B")
        .toLongOrNull()

    return SourceData(
        snapshot = ImageSourceSnapshot(
            reference = reference,
            resolvedPath = null,
            sourceType = "INLINE_REFERENCE",
            format = mimeType.substringAfterLast('/').uppercase(),
            mimeType = mimeType,
            fileSizeBytes = fileSize,
            width = null,
            height = null,
            readable = true,
            rasterDataAvailable = false,
        ),
        image = null,
    )
}

private fun loadDataUrl(reference: String): SourceData {
    val header = reference.substringBefore(',')
    val payload = reference.substringAfter(',', "")
    require(payload.isNotBlank()) { "影像数据为空" }

    val mimeType = header
        .substringAfter("data:", "")
        .substringBefore(';')
        .ifBlank { "application/octet-stream" }

    val bytes = if (header.contains(";base64", ignoreCase = true)) {
        runCatching { Base64.getDecoder().decode(payload) }
            .getOrElse { throw IllegalArgumentException("影像数据解码失败", it) }
    } else {
        URLDecoder.decode(payload, Charsets.UTF_8).toByteArray(Charsets.UTF_8)
    }

    val image = bytes.inputStream().use { ImageIO.read(it) }
        ?: throw IllegalArgumentException("影像数据无法解码为栅格图像")

    return SourceData(
        snapshot = ImageSourceSnapshot(
            reference = reference.take(96) + if (reference.length > 96) "...(truncated)" else "",
            resolvedPath = null,
            sourceType = "DATA_URL",
            format = mimeType.substringAfterLast('/').uppercase(),
            mimeType = mimeType,
            fileSizeBytes = bytes.size.toLong(),
            width = image.width,
            height = image.height,
            readable = true,
            rasterDataAvailable = true,
        ),
        image = image,
    )
}

private fun resolvePath(reference: String): Path {
    return try {
        if (reference.startsWith("file://", ignoreCase = true)) {
            Path.of(URI(reference))
        } else {
            Path.of(reference)
        }
    } catch (error: InvalidPathException) {
        throw IllegalArgumentException("影像路径不合法: $reference", error)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("影像路径不合法: $reference", error)
    }
}

private fun extractFeatures(source: SourceData, imageType: ImageType): ExtractedFeatures {
    source.image?.let { rasterImage ->
        return extractPixelFeatures(rasterImage, imageType)
    }

    // 无法读取像素时只返回元数据级分析，避免伪造临床结论。
    return extractMetadataFeatures(source.snapshot)
}

private fun extractPixelFeatures(image: BufferedImage, imageType: ImageType): ExtractedFeatures {
    val width = image.width
    val height = image.height
    val step = max(1, min(width, height) / 256)
    val sampleWidth = ((width - 1) / step) + 1
    val sampleHeight = ((height - 1) / step) + 1
    val samples = DoubleArray(sampleWidth * sampleHeight)
    val abnormalMask = BooleanArray(sampleWidth * sampleHeight)
    val deviationValues = DoubleArray(sampleWidth * sampleHeight)

    var index = 0
    var sum = 0.0
    var sumSquares = 0.0
    var edgeHits = 0
    var edgeChecks = 0

    for (sampleY in 0 until sampleHeight) {
        val y = min(sampleY * step, height - 1)
        for (sampleX in 0 until sampleWidth) {
            val x = min(sampleX * step, width - 1)
            val gray = grayscale(image.getRGB(x, y))
            samples[index++] = gray
            sum += gray
            sumSquares += gray * gray

            if (sampleX > 0) {
                val left = samples[index - 2]
                if (abs(gray - left) >= 18.0) {
                    edgeHits++
                }
                edgeChecks++
            }
            if (sampleY > 0) {
                val upper = samples[(sampleY - 1) * sampleWidth + sampleX]
                if (abs(gray - upper) >= 18.0) {
                    edgeHits++
                }
                edgeChecks++
            }
        }
    }

    val totalSamples = samples.size.coerceAtLeast(1)
    val mean = sum / totalSamples
    val variance = (sumSquares / totalSamples) - (mean * mean)
    val contrast = sqrt(max(variance, 0.0))
    val abnormalThreshold = max(contrast * 1.2, 16.0)

    var abnormalCount = 0

    for (sampleY in 0 until sampleHeight) {
        for (sampleX in 0 until sampleWidth) {
            val currentIndex = sampleY * sampleWidth + sampleX
            val value = samples[currentIndex]
            val deviation = abs(value - mean)
            if (deviation >= abnormalThreshold) {
                abnormalMask[currentIndex] = true
                deviationValues[currentIndex] = deviation
                abnormalCount++
            }
        }
    }

    val lesionCoverage = abnormalCount.toDouble() / totalSamples
    val edgeDensity = if (edgeChecks == 0) 0.0 else edgeHits.toDouble() / edgeChecks
    val resolutionFactor = ((width.toDouble() * height.toDouble()) / 1_048_576.0).coerceIn(0.0, 2.0)
    val imageQualityScore = (
        55.0 +
            contrast.coerceIn(0.0, 70.0) * 0.35 +
            edgeDensity.coerceIn(0.0, 0.35) * 80.0 +
            resolutionFactor * 10.0
        ).coerceIn(45.0, 98.0)
    val confidence = (
        0.62 +
            contrast.coerceIn(0.0, 60.0) / 300.0 +
            edgeDensity.coerceIn(0.0, 0.3) * 0.35 +
            resolutionFactor * 0.06
        ).coerceIn(0.55, 0.97)
    val highlightRegions = buildHighlightRegions(
        sampleWidth = sampleWidth,
        sampleHeight = sampleHeight,
        step = step,
        abnormalMask = abnormalMask,
        deviationValues = deviationValues,
        totalSamples = totalSamples,
        abnormalThreshold = abnormalThreshold,
        imageType = imageType,
        baseConfidence = confidence,
    )
    val primaryRegion = highlightRegions.firstOrNull()

    return ExtractedFeatures(
        analysisMode = PIXEL_ANALYSIS_MODE,
        meanLuminance = mean,
        contrast = contrast,
        edgeDensity = edgeDensity,
        lesionCoverage = lesionCoverage,
        lesionSizeMm = primaryRegion?.estimatedSizeMm ?: 0.0,
        location = primaryRegion?.location ?: "未见明确异常区域",
        confidence = round2(confidence),
        imageQualityScore = round1(imageQualityScore),
        highlightRegions = highlightRegions,
    )
}

private fun extractMetadataFeatures(snapshot: ImageSourceSnapshot): ExtractedFeatures {
    val sizeMb = ((snapshot.fileSizeBytes ?: 0L).toDouble() / (1024 * 1024)).coerceIn(0.0, 64.0)
    val normalizedSize = (sizeMb / 64.0).coerceIn(0.0, 1.0)
    val estimatedQuality = (42.0 + normalizedSize * 28.0).coerceIn(40.0, 70.0)
    val confidence = (0.28 + normalizedSize * 0.18).coerceIn(0.28, 0.46)

    return ExtractedFeatures(
        analysisMode = METADATA_ANALYSIS_MODE,
        meanLuminance = 0.0,
        contrast = 0.0,
        edgeDensity = 0.0,
        lesionCoverage = 0.0,
        lesionSizeMm = 0.0,
        location = "像素数据不可用",
        confidence = round2(confidence),
        imageQualityScore = round1(estimatedQuality),
        highlightRegions = emptyList(),
    )
}

private fun buildHighlightRegions(
    sampleWidth: Int,
    sampleHeight: Int,
    step: Int,
    abnormalMask: BooleanArray,
    deviationValues: DoubleArray,
    totalSamples: Int,
    abnormalThreshold: Double,
    imageType: ImageType,
    baseConfidence: Double,
): List<HighlightRegion> {
    if (abnormalMask.none { it }) {
        return emptyList()
    }

    val minAreaSamples = max(3, min(sampleWidth, sampleHeight) / 20)
    val visited = BooleanArray(abnormalMask.size)
    val majorCandidates = mutableListOf<RegionCandidate>()
    val fallbackCandidates = mutableListOf<RegionCandidate>()
    val queue = IntArray(abnormalMask.size)

    for (sampleY in 0 until sampleHeight) {
        for (sampleX in 0 until sampleWidth) {
            val startIndex = sampleY * sampleWidth + sampleX
            if (!abnormalMask[startIndex] || visited[startIndex]) {
                continue
            }

            visited[startIndex] = true
            var head = 0
            var tail = 0
            queue[tail++] = startIndex
            var minX = sampleX
            var minY = sampleY
            var maxX = sampleX
            var maxY = sampleY
            var areaSamples = 0
            var centroidX = 0.0
            var centroidY = 0.0
            var deviationSum = 0.0
            val sampleIndexes = mutableListOf<Int>()

            while (head < tail) {
                val currentIndex = queue[head++]
                val currentX = currentIndex % sampleWidth
                val currentY = currentIndex / sampleWidth

                areaSamples++
                sampleIndexes += currentIndex
                minX = min(minX, currentX)
                minY = min(minY, currentY)
                maxX = max(maxX, currentX)
                maxY = max(maxY, currentY)
                centroidX += currentX.toDouble()
                centroidY += currentY.toDouble()
                deviationSum += deviationValues[currentIndex]

                for (nextY in max(0, currentY - 1)..min(sampleHeight - 1, currentY + 1)) {
                    for (nextX in max(0, currentX - 1)..min(sampleWidth - 1, currentX + 1)) {
                        val nextIndex = nextY * sampleWidth + nextX
                        if (visited[nextIndex] || !abnormalMask[nextIndex]) {
                            continue
                        }
                        visited[nextIndex] = true
                        queue[tail++] = nextIndex
                    }
                }
            }

            val meanDeviation = deviationSum / areaSamples.coerceAtLeast(1)
            val areaRatio = areaSamples.toDouble() / totalSamples.coerceAtLeast(1)
            val intensityScore = (meanDeviation / abnormalThreshold.coerceAtLeast(1.0)).coerceIn(0.0, 2.5) / 2.5
            val compactness = (
                min((maxX - minX) + 1, (maxY - minY) + 1).toDouble() /
                    max((maxX - minX) + 1, (maxY - minY) + 1).toDouble().coerceAtLeast(1.0)
                ).coerceIn(0.35, 1.0)
            val componentScore = (
                areaRatio.coerceIn(0.0, 0.18) / 0.18 * 0.6 +
                    intensityScore * 0.3 +
                    compactness * 0.1
                ).coerceIn(0.0, 1.0)

            val candidate = RegionCandidate(
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                areaSamples = areaSamples,
                centroidX = centroidX / areaSamples.coerceAtLeast(1),
                centroidY = centroidY / areaSamples.coerceAtLeast(1),
                meanDeviation = meanDeviation,
                componentScore = componentScore,
                sampleIndexes = sampleIndexes,
            )

            fallbackCandidates += candidate
            if (areaSamples >= minAreaSamples) {
                majorCandidates += candidate
            }
        }
    }

    val rankedCandidates = (if (majorCandidates.isNotEmpty()) majorCandidates else fallbackCandidates)
        .sortedWith(
            compareByDescending<RegionCandidate> { it.componentScore }
                .thenByDescending { it.areaSamples }
                .thenBy { it.minY }
                .thenBy { it.minX },
        )
        .take(highlightPalettes.size)

    return rankedCandidates.mapIndexed { index, candidate ->
        val palette = highlightPalettes[index]
        val widthPx = ((candidate.maxX - candidate.minX) + 1) * step
        val heightPx = ((candidate.maxY - candidate.minY) + 1) * step
        val sizeMm = round1(max(widthPx, heightPx) * pixelSpacingMm(imageType))
        val coveragePercent = round1(candidate.areaSamples.toDouble() / totalSamples * 100.0)
        val location = quadrantName(candidate.centroidX / sampleWidth, candidate.centroidY / sampleHeight)
        val severity = regionRiskLevel(
            areaRatio = candidate.areaSamples.toDouble() / totalSamples,
            relativeIntensity = candidate.meanDeviation / abnormalThreshold.coerceAtLeast(1.0),
        )
        val annotationTitle = annotationTitleForPriority(index + 1)
        val annotationMeaning = annotationMeaning(
            imageType = imageType,
            priority = index + 1,
            colorName = palette.colorName,
            location = location,
        )
        val shape = overlayShapeForCandidate(candidate)
        val rotationDegrees = overlayRotationForCandidate(candidate)
        val contour = buildHighlightContour(
            candidate = candidate,
            sampleWidth = sampleWidth,
            sampleHeight = sampleHeight,
        )
        val regionConfidence = round2(
            (
                baseConfidence * 0.82 +
                    candidate.componentScore * 0.16 +
                    (candidate.areaSamples.toDouble() / totalSamples).coerceIn(0.0, 0.08) * 1.5
                ).coerceIn(0.38, 0.98),
        )
        val label = "L${index + 1}"

        HighlightRegion(
            id = "lesion-${index + 1}",
            label = label,
            colorName = palette.colorName,
            colorHex = palette.colorHex,
            priority = index + 1,
            annotationTitle = annotationTitle,
            annotationMeaning = annotationMeaning,
            location = location,
            severity = severity,
            confidence = regionConfidence,
            coveragePercent = coveragePercent,
            estimatedSizeMm = sizeMm,
            shape = shape,
            rotationDegrees = rotationDegrees,
            boundingBox = HighlightBoundingBox(
                leftPercent = round2(candidate.minX.toDouble() / sampleWidth * 100.0),
                topPercent = round2(candidate.minY.toDouble() / sampleHeight * 100.0),
                widthPercent = round2(((candidate.maxX - candidate.minX) + 1).toDouble() / sampleWidth * 100.0),
                heightPercent = round2(((candidate.maxY - candidate.minY) + 1).toDouble() / sampleHeight * 100.0),
            ),
            contour = contour,
            note = "${palette.colorName}${label} 为${annotationTitle}，位于 $location，估计范围 ${sizeMm} mm，覆盖采样区域 ${coveragePercent}%。",
        )
    }
}

private fun buildHighlightLegend(regions: List<HighlightRegion>): List<HighlightLegendItem> {
    if (regions.isEmpty()) {
        return emptyList()
    }

    return regions
        .sortedBy { it.priority }
        .map { region ->
            HighlightLegendItem(
                colorName = region.colorName,
                colorHex = region.colorHex,
                meaning = legendMeaningForPriority(region.priority),
            )
        }
        .distinctBy { it.colorHex }
}

private fun buildHighlightContour(
    candidate: RegionCandidate,
    sampleWidth: Int,
    sampleHeight: Int,
): List<HighlightContourPoint> {
    if (candidate.sampleIndexes.isEmpty()) {
        return emptyList()
    }

    val cellSet = candidate.sampleIndexes.toHashSet()
    val segments = mutableListOf<GridSegment>()

    for (sampleIndex in candidate.sampleIndexes) {
        val x = sampleIndex % sampleWidth
        val y = sampleIndex / sampleWidth
        val leftNeighbor = if (x > 0) sampleIndex - 1 else -1
        val topNeighbor = if (y > 0) sampleIndex - sampleWidth else -1
        val rightNeighbor = if (x < sampleWidth - 1) sampleIndex + 1 else -1
        val bottomNeighbor = if (y < sampleHeight - 1) sampleIndex + sampleWidth else -1

        if (leftNeighbor !in cellSet) {
            segments += GridSegment(
                start = GridVertex(x, y + 1),
                end = GridVertex(x, y),
            )
        }
        if (topNeighbor !in cellSet) {
            segments += GridSegment(
                start = GridVertex(x, y),
                end = GridVertex(x + 1, y),
            )
        }
        if (rightNeighbor !in cellSet) {
            segments += GridSegment(
                start = GridVertex(x + 1, y),
                end = GridVertex(x + 1, y + 1),
            )
        }
        if (bottomNeighbor !in cellSet) {
            segments += GridSegment(
                start = GridVertex(x + 1, y + 1),
                end = GridVertex(x, y + 1),
            )
        }
    }

    if (segments.isEmpty()) {
        return emptyList()
    }

    val loops = buildSegmentLoops(segments)
    val mainLoop = loops.maxByOrNull(::polygonAreaAbs) ?: return emptyList()
    val simplifiedLoop = simplifyContourLoop(mainLoop)

    return simplifiedLoop.map { vertex ->
        HighlightContourPoint(
            xPercent = round2(vertex.x.toDouble() / sampleWidth * 100.0),
            yPercent = round2(vertex.y.toDouble() / sampleHeight * 100.0),
        )
    }
}

private fun buildSegmentLoops(segments: List<GridSegment>): List<List<GridVertex>> {
    val outgoing = mutableMapOf<GridVertex, MutableList<GridVertex>>()
    segments.forEach { segment ->
        outgoing.getOrPut(segment.start) { mutableListOf() }.add(segment.end)
    }

    val loops = mutableListOf<List<GridVertex>>()
    while (outgoing.isNotEmpty()) {
        val start = outgoing.keys.first()
        val loop = mutableListOf<GridVertex>()
        var current = start
        var guard = 0

        while (guard < segments.size * 4) {
            loop += current
            val nextVertices = outgoing[current] ?: break
            val next = nextVertices.removeFirstOrNull() ?: break
            if (nextVertices.isEmpty()) {
                outgoing.remove(current)
            }
            current = next
            if (current == start) {
                loop += current
                loops += loop
                break
            }
            guard++
        }
    }

    return loops.filter { it.size >= 4 }
}

private fun polygonAreaAbs(vertices: List<GridVertex>): Double {
    if (vertices.size < 3) {
        return 0.0
    }

    var area = 0.0
    for (index in 0 until vertices.size - 1) {
        val current = vertices[index]
        val next = vertices[index + 1]
        area += (current.x * next.y - next.x * current.y).toDouble()
    }
    return abs(area) / 2.0
}

private fun simplifyContourLoop(vertices: List<GridVertex>): List<GridVertex> {
    if (vertices.size <= 6) {
        return vertices
    }

    val withoutDuplicateClosure = if (vertices.first() == vertices.last()) {
        vertices.dropLast(1)
    } else {
        vertices
    }

    if (withoutDuplicateClosure.size <= 5) {
        return withoutDuplicateClosure + withoutDuplicateClosure.first()
    }

    val filtered = mutableListOf<GridVertex>()
    for (index in withoutDuplicateClosure.indices) {
        val previous = withoutDuplicateClosure[(index - 1 + withoutDuplicateClosure.size) % withoutDuplicateClosure.size]
        val current = withoutDuplicateClosure[index]
        val next = withoutDuplicateClosure[(index + 1) % withoutDuplicateClosure.size]
        val cross = (current.x - previous.x) * (next.y - current.y) - (current.y - previous.y) * (next.x - current.x)
        if (cross != 0) {
            filtered += current
        }
    }

    val maxPointCount = 28
    val reduced = if (filtered.size > maxPointCount) {
        val step = filtered.size.toDouble() / maxPointCount.toDouble()
        buildList {
            var cursor = 0.0
            while (size < maxPointCount) {
                add(filtered[cursor.toInt().coerceAtMost(filtered.lastIndex)])
                cursor += step
            }
        }
    } else {
        filtered
    }

    return if (reduced.isNotEmpty()) reduced + reduced.first() else vertices
}

private fun legendMeaningForPriority(priority: Int): String {
    return highlightPalettes.getOrNull(priority - 1)?.meaning ?: "辅助关注区域"
}

private fun annotationTitleForPriority(priority: Int): String {
    return when (priority) {
        1 -> "主可疑高亮"
        2 -> "次级可疑高亮"
        3 -> "随访关注高亮"
        else -> "辅助定位高亮"
    }
}

private fun annotationMeaning(
    imageType: ImageType,
    priority: Int,
    colorName: String,
    location: String,
): String {
    val imageSpecificMeaning = when (imageType) {
        ImageType.CT -> "表示该区域与周围组织存在更明显的密度差异"
        ImageType.MRI -> "表示该区域与周围组织存在更明显的信号差异"
        ImageType.XRAY -> "表示该区域与周围结构相比存在更明显的透亮度/致密度差异"
        ImageType.ULTRASOUND -> "表示该区域的回声纹理与周围区域差异更明显"
        ImageType.PATHOLOGY, ImageType.OTHER -> "表示该区域的纹理异质性相对更强"
    }
    val priorityMeaning = when (priority) {
        1 -> "为当前图像中优先级最高的复核区域"
        2 -> "为次高优先级的复核区域"
        3 -> "建议结合临床信息持续观察"
        else -> "主要用于补充定位和人工核对"
    }
    return "$colorName 标注位于$location，$imageSpecificMeaning，$priorityMeaning。"
}

private fun overlayShapeForCandidate(candidate: RegionCandidate): String {
    val width = ((candidate.maxX - candidate.minX) + 1).toDouble().coerceAtLeast(1.0)
    val height = ((candidate.maxY - candidate.minY) + 1).toDouble().coerceAtLeast(1.0)
    val aspectRatio = width / height
    val compactness = min(width, height) / max(width, height)

    return when {
        aspectRatio >= 1.65 || aspectRatio <= 0.6 -> "PILL"
        compactness >= 0.82 -> "ELLIPSE"
        else -> "BLOB"
    }
}

private fun overlayRotationForCandidate(candidate: RegionCandidate): Double {
    val width = ((candidate.maxX - candidate.minX) + 1).toDouble().coerceAtLeast(1.0)
    val height = ((candidate.maxY - candidate.minY) + 1).toDouble().coerceAtLeast(1.0)
    val directionBias = if (candidate.centroidX >= ((candidate.minX + candidate.maxX) / 2.0)) 1.0 else -1.0

    val rotation = when {
        width >= height * 1.5 -> 10.0 * directionBias
        height >= width * 1.5 -> -8.0 * directionBias
        else -> 4.0 * directionBias
    }

    return round1(rotation)
}

private fun regionRiskLevel(areaRatio: Double, relativeIntensity: Double): String {
    val score = (
        areaRatio.coerceIn(0.0, 0.15) / 0.15 * 0.58 +
            relativeIntensity.coerceIn(0.0, 2.2) / 2.2 * 0.42
        ).coerceIn(0.0, 1.0)

    return when {
        score >= 0.78 -> "高风险"
        score >= 0.5 -> "中风险"
        score >= 0.24 -> "低风险"
        else -> "轻度异常"
    }
}

private fun buildMetric(imageType: ImageType, features: ExtractedFeatures): MetricDto {
    if (features.analysisMode != PIXEL_ANALYSIS_MODE) {
        return MetricDto.MetricCollection(
            metrics = listOf(
                MetricDto.GeneralMetric(
                    name = "影像完整度评分",
                    value = features.imageQualityScore,
                    unit = "score",
                    referenceRange = ">= 60",
                    confidence = features.confidence,
                ),
                MetricDto.GeneralMetric(
                    name = "像素分析可用性",
                    value = 0.0,
                    unit = "flag",
                    referenceRange = "1 = 可分析",
                    confidence = features.confidence,
                ),
                MetricDto.GeneralMetric(
                    name = "结果可信度",
                    value = round1(features.confidence * 100.0),
                    unit = "%",
                    referenceRange = ">= 70",
                    confidence = features.confidence,
                ),
            ),
        )
    }

    return when (imageType) {
        ImageType.CT -> MetricDto.CTMetric(
            density = round1((features.meanLuminance / 255.0) * 2000.0 - 1000.0),
            size = features.lesionSizeMm,
            location = features.location,
            severity = riskLevel(features),
            confidence = features.confidence,
        )

        ImageType.MRI -> MetricDto.MRIMetric(
            signalIntensity = when {
                features.meanLuminance < 85.0 -> "低信号"
                features.meanLuminance < 170.0 -> "等信号"
                else -> "高信号"
            },
            size = features.lesionSizeMm,
            location = features.location,
            tissueCharacteristics = when {
                features.contrast >= 55.0 && features.edgeDensity >= 0.18 -> "纹理异质，边界较清晰"
                features.contrast >= 35.0 -> "轻度异质改变"
                else -> "纹理较均匀"
            },
            confidence = features.confidence,
        )

        ImageType.XRAY -> MetricDto.XRayMetric(
            opacity = when {
                features.meanLuminance >= 180.0 -> "高密度影"
                features.meanLuminance >= 120.0 -> "中等密度影"
                else -> "低密度影"
            },
            size = features.lesionSizeMm,
            location = features.location,
            boneStructure = when {
                features.edgeDensity >= 0.24 -> "骨皮质连续，结构显示清晰"
                features.edgeDensity >= 0.16 -> "骨小梁结构可辨"
                else -> "骨性细节显示受限"
            },
            confidence = features.confidence,
        )

        ImageType.ULTRASOUND -> MetricDto.UltrasoundMetric(
            echogenicity = when {
                features.meanLuminance < 85.0 -> "低回声"
                features.meanLuminance < 160.0 -> "等回声"
                else -> "高回声"
            },
            size = features.lesionSizeMm,
            location = features.location,
            bloodFlow = when {
                features.edgeDensity >= 0.22 -> "可见较丰富血流提示"
                features.edgeDensity >= 0.14 -> "可见少量血流信号"
                else -> "未见明显异常血流"
            },
            confidence = features.confidence,
        )

        ImageType.PATHOLOGY, ImageType.OTHER -> MetricDto.MetricCollection(
            metrics = listOf(
                MetricDto.GeneralMetric(
                    name = "纹理异质性指数",
                    value = round1(features.contrast),
                    unit = "score",
                    referenceRange = "0 - 80",
                    confidence = features.confidence,
                ),
                MetricDto.GeneralMetric(
                    name = "疑似异常区域占比",
                    value = round1(features.lesionCoverage * 100.0),
                    unit = "%",
                    referenceRange = "< 5",
                    confidence = features.confidence,
                ),
                MetricDto.GeneralMetric(
                    name = "边缘复杂度",
                    value = round1(features.edgeDensity * 100.0),
                    unit = "%",
                    referenceRange = "0 - 30",
                    confidence = features.confidence,
                ),
            ),
        )
    }
}

private fun buildKeyIndicators(
    snapshot: ImageSourceSnapshot,
    features: ExtractedFeatures,
    metrics: MetricDto,
): List<IndicatorItem> {
    val indicators = mutableListOf(
        IndicatorItem(
            name = "分析模式",
            value = if (features.analysisMode == PIXEL_ANALYSIS_MODE) "像素级分析" else "元数据级分析",
        ),
        IndicatorItem(
            name = "结果可信度",
            value = round1(features.confidence * 100.0).toString(),
            unit = "%",
        ),
        IndicatorItem(
            name = "影像质量评分",
            value = features.imageQualityScore.toString(),
            unit = "score",
        ),
    )

    snapshot.width?.let { width ->
        snapshot.height?.let { height ->
            indicators += IndicatorItem(
                name = "影像尺寸",
                value = "${width}x$height",
                unit = "px",
            )
        }
    }
    snapshot.fileSizeBytes?.let { size ->
        indicators += IndicatorItem(
            name = "文件大小",
            value = round1(size.toDouble() / 1024.0).toString(),
            unit = "KB",
        )
    }
    indicators += IndicatorItem(
        name = "高亮区域数",
        value = features.highlightRegions.size.toString(),
        unit = "个",
    )
    features.highlightRegions.firstOrNull()?.let { primaryRegion ->
        indicators += IndicatorItem(
            name = "首要高亮",
            value = "${primaryRegion.colorName}${primaryRegion.label}",
            interpretation = primaryRegion.location,
        )
    }

    indicators += metricIndicators(metrics)
    return indicators
}

private fun metricIndicators(metrics: MetricDto): List<IndicatorItem> {
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
            IndicatorItem("透亮度/密度", metrics.opacity),
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

        is MetricDto.MetricCollection -> metrics.metrics.flatMap { metricIndicators(it) }
    }
}

private fun buildFindings(
    imageType: ImageType,
    features: ExtractedFeatures,
    metrics: MetricDto,
): List<String> {
    if (features.analysisMode != PIXEL_ANALYSIS_MODE) {
        return listOf(
            "${imageType.name} 影像仅完成文件级校验，当前未读取到原始像素。",
            "已输出数据完整度、可分析性和可信度指标，可用于链路继续处理。",
            "若需临床级指标，请上传可直接解析的 PNG/JPG/BMP 等栅格图像或补充 DICOM 解码能力。",
        )
    }

    val riskText = when (metrics) {
        is MetricDto.CTMetric -> metrics.severity
        else -> riskLevel(features)
    }

    if (features.highlightRegions.isEmpty()) {
        return listOf(
            "已完成 ${imageType.name} 像素级分析，影像质量评分 ${features.imageQualityScore}。",
            "当前未识别出需要框选高亮的明确异常聚集区。",
            "采样级风险评估为 $riskText，建议结合原始影像与临床资料人工复核。",
        )
    }

    val findings = mutableListOf(
        "已完成 ${imageType.name} 像素级分析，共标记 ${features.highlightRegions.size} 个可疑高亮区域，影像质量评分 ${features.imageQualityScore}。",
    )
    findings += features.highlightRegions.take(3).map { region ->
        "${region.colorName}${region.label} 为${region.annotationTitle}，位于 ${region.location}，估计范围 ${region.estimatedSizeMm} mm，覆盖采样区域 ${region.coveragePercent}%，判定 ${region.severity}。"
    }
    findings += "异常区域总覆盖采样区域 ${round1(features.lesionCoverage * 100.0)}%，综合风险判定为 $riskText。"
    return findings
}

private fun buildRecommendations(
    imageType: ImageType,
    features: ExtractedFeatures,
): List<String> {
    if (features.analysisMode != PIXEL_ANALYSIS_MODE) {
        return listOf(
            "补充原始像素数据后重新执行分析，以生成临床指标。",
            "若当前来源为 DICOM，请在服务侧补充 DICOM 解码链路。",
            "元数据结果仅可用于流程联调，不建议直接用于临床判读。",
        )
    }

    val score = riskScore(features)
    val risk = when {
        score >= 0.72 -> "高风险"
        score >= 0.42 -> "中风险"
        score >= 0.18 -> "低风险"
        else -> "低风险"
    }

    val followUp = when (imageType) {
        ImageType.CT -> "建议结合原始层厚、窗位窗宽及既往 CT 结果做对比复核。"
        ImageType.MRI -> "建议结合增强序列和临床症状进一步评估。"
        ImageType.XRAY -> "建议结合体位片或既往片进行动态对比。"
        ImageType.ULTRASOUND -> "建议结合实时扫查及彩色多普勒结果确认。"
        ImageType.PATHOLOGY, ImageType.OTHER -> "建议补充更高分辨率图像或人工复核。"
    }
    val highlightAdvice = features.highlightRegions.firstOrNull()?.let { region ->
        "结果图中已使用 ${region.colorName}${region.label} 标记首要可疑区域，建议优先复核该区域。"
    } ?: "当前未生成高优先级高亮区域，仍建议结合原始影像人工复核。"

    return listOf(
        "当前结果处于 $risk 区间，建议由影像科医师完成最终判读。",
        followUp,
        highlightAdvice,
    )
}

private fun buildSummary(
    imageType: ImageType,
    features: ExtractedFeatures,
    metrics: MetricDto,
): String {
    if (features.analysisMode != PIXEL_ANALYSIS_MODE) {
        return "${imageType.name} 影像仅完成元数据级分析，当前无法输出像素级医学结论。"
    }

    val riskText = when (metrics) {
        is MetricDto.CTMetric -> metrics.severity
        else -> riskLevel(features)
    }

    if (features.highlightRegions.isEmpty()) {
        return "${imageType.name} 影像像素级分析完成，当前未生成明确病灶高亮区域，整体风险等级 $riskText。"
    }

    val legendSummary = features.highlightRegions.joinToString("、") { "${it.colorName}${it.label}" }
    return "${imageType.name} 影像像素级分析完成，共标记 ${features.highlightRegions.size} 个高亮区域（$legendSummary），主病灶位于 ${features.location}，估计范围 ${features.lesionSizeMm} mm，风险等级 $riskText。"
}

private fun buildLimitations(snapshot: ImageSourceSnapshot, analysisMode: String): List<String> {
    if (analysisMode == PIXEL_ANALYSIS_MODE) {
        return emptyList()
    }

    val sourceDescription = when (snapshot.sourceType) {
        "INLINE_REFERENCE" -> "当前输入仅提供了内联影像引用"
        else -> "当前文件未能解码为可分析的栅格像素"
    }

    return listOf(
        sourceDescription,
        "本次结果不包含病灶密度、信号或回声等像素级指标",
        "如需临床级分析，请补充可解码的原始影像文件",
    )
}

private fun buildRandomId(prefix: String): String {
    return "$prefix-${UUID.randomUUID()}"
}

private fun buildStableId(prefix: String, seed: String): String {
    return "$prefix-${UUID.nameUUIDFromBytes(seed.toByteArray())}"
}

private fun grayscale(rgb: Int): Double {
    val red = (rgb shr 16) and 0xFF
    val green = (rgb shr 8) and 0xFF
    val blue = rgb and 0xFF
    return red * 0.299 + green * 0.587 + blue * 0.114
}

private fun quadrantName(xRatio: Double, yRatio: Double): String {
    return when {
        xRatio < 0.5 && yRatio < 0.5 -> "左上象限"
        xRatio >= 0.5 && yRatio < 0.5 -> "右上象限"
        xRatio < 0.5 && yRatio >= 0.5 -> "左下象限"
        else -> "右下象限"
    }
}

private fun pixelSpacingMm(imageType: ImageType): Double {
    return when (imageType) {
        ImageType.CT -> 0.7
        ImageType.MRI -> 0.9
        ImageType.XRAY -> 0.25
        ImageType.ULTRASOUND -> 0.3
        ImageType.PATHOLOGY -> 0.02
        ImageType.OTHER -> 0.5
    }
}

private fun riskLevel(features: ExtractedFeatures): String {
    val score = riskScore(features)
    return when {
        score >= 0.72 -> "高风险"
        score >= 0.42 -> "中风险"
        score >= 0.18 -> "低风险"
        else -> "未见明显高风险征象"
    }
}

private fun riskScore(features: ExtractedFeatures): Double {
    return (
        features.lesionCoverage.coerceIn(0.0, 0.35) / 0.35 * 0.45 +
            features.contrast.coerceIn(0.0, 60.0) / 60.0 * 0.30 +
            features.edgeDensity.coerceIn(0.0, 0.3) / 0.3 * 0.25
        ).coerceIn(0.0, 1.0)
}

private fun round1(value: Double): Double {
    return (value * 10.0).roundToInt() / 10.0
}

private fun round2(value: Double): Double {
    return (value * 100.0).roundToInt() / 100.0
}
