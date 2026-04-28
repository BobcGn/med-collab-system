package aiagent.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal data class SegmentationServiceSettings(
    val enabled: Boolean,
    val baseUrl: String,
    val timeoutSeconds: Long,
) {
    companion object {
        val DISABLED = SegmentationServiceSettings(
            enabled = false,
            baseUrl = "http://127.0.0.1:8099",
            timeoutSeconds = 30,
        )
    }

    fun endpointUri(): URI {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val endpoint = if (normalizedBaseUrl.endsWith("/api/v1/segment")) {
            normalizedBaseUrl
        } else {
            "$normalizedBaseUrl/api/v1/segment"
        }
        return URI.create(endpoint)
    }
}

@Serializable
internal data class SegmentationServiceRequest(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("hospital_id")
    val hospitalId: String,
    @SerialName("patient_id")
    val patientId: String,
    @SerialName("patient_name")
    val patientName: String,
    @SerialName("image_type")
    val imageType: String,
    val image: SegmentationImageReference,
    val options: SegmentationOptions = SegmentationOptions(),
)

@Serializable
internal data class SegmentationImageReference(
    @SerialName("source_type")
    val sourceType: String,
    val value: String,
    @SerialName("mime_type")
    val mimeType: String? = null,
)

@Serializable
internal data class SegmentationOptions(
    @SerialName("return_mask")
    val returnMask: Boolean = true,
    @SerialName("return_contour")
    val returnContour: Boolean = true,
    @SerialName("return_metrics")
    val returnMetrics: Boolean = true,
)

@Serializable
internal data class SegmentationServiceResponse(
    @SerialName("request_id")
    val requestId: String,
    val status: String,
    val message: String,
    @SerialName("hospital_id")
    val hospitalId: String,
    @SerialName("patient_id")
    val patientId: String,
    @SerialName("patient_name")
    val patientName: String,
    @SerialName("image_type")
    val imageType: String,
    val model: SegmentationModelDescriptor,
    val timing: SegmentationTiming,
    @SerialName("quality_gate")
    val qualityGate: SegmentationQualityGate,
    val artifacts: SegmentationArtifacts,
    val regions: List<SegmentationRegion>,
)

@Serializable
internal data class SegmentationModelDescriptor(
    val name: String,
    val version: String,
    val backend: String,
    val device: String,
    @SerialName("input_height")
    val inputHeight: Int,
    @SerialName("input_width")
    val inputWidth: Int,
    @SerialName("in_channels")
    val inChannels: Int,
    @SerialName("output_classes")
    val outputClasses: Int,
    @SerialName("weights_path")
    val weightsPath: String? = null,
    @SerialName("weights_loaded")
    val weightsLoaded: Boolean,
)

@Serializable
internal data class SegmentationTiming(
    @SerialName("preprocess_ms")
    val preprocessMs: Int,
    @SerialName("inference_ms")
    val inferenceMs: Int,
    @SerialName("postprocess_ms")
    val postprocessMs: Int,
)

@Serializable
internal data class SegmentationQualityGate(
    val status: String,
    val reason: String,
)

@Serializable
internal data class SegmentationArtifacts(
    @SerialName("mask_path")
    val maskPath: String? = null,
    @SerialName("overlay_path")
    val overlayPath: String? = null,
    @SerialName("metadata_path")
    val metadataPath: String? = null,
)

@Serializable
internal data class SegmentationRegion(
    @SerialName("region_id")
    val regionId: String,
    @SerialName("class_name")
    val className: String,
    val label: String,
    val confidence: Double,
    val location: String,
    val severity: String,
    @SerialName("coverage_percent")
    val coveragePercent: Double,
    @SerialName("estimated_size_mm")
    val estimatedSizeMm: Double,
    @SerialName("bounding_box")
    val boundingBox: SegmentationBoundingBox,
    val contour: List<SegmentationContourPoint> = emptyList(),
)

@Serializable
internal data class SegmentationBoundingBox(
    @SerialName("left_percent")
    val leftPercent: Double,
    @SerialName("top_percent")
    val topPercent: Double,
    @SerialName("width_percent")
    val widthPercent: Double,
    @SerialName("height_percent")
    val heightPercent: Double,
)

@Serializable
internal data class SegmentationContourPoint(
    @SerialName("x_percent")
    val xPercent: Double,
    @SerialName("y_percent")
    val yPercent: Double,
)

internal object SegmentationServiceClient {
    fun segment(
        settings: SegmentationServiceSettings,
        request: SegmentationServiceRequest,
    ): SegmentationServiceResponse {
        require(settings.enabled) { "分割服务未启用" }

        val timeout = Duration.ofSeconds(settings.timeoutSeconds.coerceAtLeast(1))
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build()
        val requestBody = toolJson.encodeToString(SegmentationServiceRequest.serializer(), request)
        val httpRequest = HttpRequest.newBuilder(settings.endpointUri())
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "分割服务请求失败: HTTP ${response.statusCode()} ${response.body().take(500)}",
            )
        }

        return runCatching {
            toolJson.decodeFromString(SegmentationServiceResponse.serializer(), response.body())
        }.getOrElse { error ->
            throw IllegalStateException("分割服务响应不符合契约: ${error.message}", error)
        }
    }
}
