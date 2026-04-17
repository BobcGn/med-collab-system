package aiagent.tools

import dto.AnalysisResultDto
import dto.ReportDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val toolJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "kind"
}

@Serializable
internal data class ToolErrorResponse(
    val errorCode: String,
    val message: String,
    val detail: String? = null,
)

@Serializable
internal data class IndicatorItem(
    val name: String,
    val value: String,
    val unit: String? = null,
    val interpretation: String? = null,
)

@Serializable
internal data class HighlightBoundingBox(
    val leftPercent: Double,
    val topPercent: Double,
    val widthPercent: Double,
    val heightPercent: Double,
)

@Serializable
internal data class HighlightContourPoint(
    val xPercent: Double,
    val yPercent: Double,
)

@Serializable
internal data class HighlightRegion(
    val id: String,
    val label: String,
    val colorName: String,
    val colorHex: String,
    val priority: Int,
    val annotationTitle: String,
    val annotationMeaning: String,
    val location: String,
    val severity: String,
    val confidence: Double,
    val coveragePercent: Double,
    val estimatedSizeMm: Double,
    val shape: String,
    val rotationDegrees: Double,
    val boundingBox: HighlightBoundingBox,
    val contour: List<HighlightContourPoint> = emptyList(),
    val note: String,
)

@Serializable
internal data class HighlightLegendItem(
    val colorName: String,
    val colorHex: String,
    val meaning: String,
)

@Serializable
internal data class ImageSourceSnapshot(
    val reference: String,
    val resolvedPath: String? = null,
    val sourceType: String,
    val format: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val readable: Boolean,
    val rasterDataAvailable: Boolean,
)

@Serializable
internal data class MedicalImageAnalysisPayload(
    val analysis: AnalysisResultDto.AnalysisResultComplete,
    val imageType: String,
    val imagePath: String,
    val analysisMode: String,
    val source: ImageSourceSnapshot,
    val keyIndicators: List<IndicatorItem>,
    val findings: List<String>,
    val summary: String,
    val recommendations: List<String>,
    val highlightRegions: List<HighlightRegion> = emptyList(),
    val highlightLegend: List<HighlightLegendItem> = emptyList(),
    val limitations: List<String> = emptyList(),
)

@Serializable
internal data class GeneratedReportPayload(
    val analysis: AnalysisResultDto.AnalysisResultComplete,
    val report: ReportDto.ReportComplete,
    val analysisId: String,
    val imageType: String,
    val analysisMode: String,
    val summary: String,
    val keyIndicators: List<IndicatorItem>,
    val findings: List<String>,
    val conclusion: String,
    val recommendations: List<String>,
    val highlightRegions: List<HighlightRegion> = emptyList(),
    val highlightLegend: List<HighlightLegendItem> = emptyList(),
    val limitations: List<String> = emptyList(),
    val reportContent: String,
)
