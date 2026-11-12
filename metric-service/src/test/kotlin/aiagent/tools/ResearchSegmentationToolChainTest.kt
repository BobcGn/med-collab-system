package aiagent.tools

import aiagent.strategy.executeControlledMetricReportPipeline
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dto.MetricDto
import enums.ImageType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResearchSegmentationToolChainTest {
    @AfterTest
    fun reset() {
        MedicalImageAnalyzerTool.configureSegmentationService(SegmentationServiceSettings.DISABLED)
    }

    @Test
    fun `research model yields pixel metrics and non diagnostic report only`() = runBlocking {
        withResearchService { image ->
            val result = executeControlledMetricReportPipeline(
                """{
                  "imagePath": ${toolJson.encodeToString(String.serializer(), image.toString())},
                  "imageType": "ULTRASOUND", "hospitalId": "H-TEST",
                  "patientId": "P-TEST", "patientName": "合成测试"
                }""".trimIndent(),
            )
            val report = toolJson.decodeFromString(GeneratedReportPayload.serializer(), result)
            try {
                assertEquals("research", report.intendedUse)
                assertEquals("ULTRASOUND_RESEARCH_REPORT", report.report.reportType)
                val metrics = report.analysis.metrics as MetricDto.MetricCollection
                assertTrue(metrics.metrics.all { it is MetricDto.GeneralMetric })
                val measures = metrics.metrics.filterIsInstance<MetricDto.GeneralMetric>()
                assertTrue(measures.all { it.confidence == null })
                assertEquals(16.0, measures.single { it.name == "前景像素面积" }.value)
                assertNull(report.highlightRegions.single().estimatedSizeMm)
                assertEquals(4, report.highlightRegions.single().widthPx)
                assertEquals("", report.highlightRegions.single().severity)
                assertContains(report.reportContent, "科研影像分割报告（非诊断）")
                assertContains(report.reportContent, "面积 16 px²")
                assertFalse(report.reportContent.contains("报告类型: 正式分析报告"))
                assertFalse(report.reportContent.contains("血流表现为"))
                assertFalse(report.reportContent.contains("0.0 mm"))
            } finally {
                report.report.filePath?.let { Files.deleteIfExists(Path.of(it)) }
            }
        }
    }

    @Test
    fun `segmentation transport sends HTTP1 JSON without h2c upgrade`() = runBlocking {
        var upgrade: String? = "request not received"
        var protocol: String? = null
        withResearchService(inspectRequest = { exchange ->
            upgrade = exchange.requestHeaders.getFirst("Upgrade")
            protocol = exchange.protocol
        }) { image ->
            val raw = MedicalImageAnalyzerTool.execute(
                MedicalImageAnalyzerTool.Args(image.toString(), ImageType.ULTRASOUND, "H-TEST", "P-TEST", "合成测试"),
            )
            assertEquals("research", toolJson.decodeFromString(MedicalImageAnalysisPayload.serializer(), raw).intendedUse)
            assertNull(upgrade)
            assertEquals("HTTP/1.1", protocol)
        }
    }

    @Test
    fun `uncalibrated research result cannot supply millimetres`() = runBlocking {
        withResearchService(transform = { response ->
            response.copy(regions = response.regions.map { it.copy(estimatedSizeMm = 2.8) })
        }) { image ->
            val error = analyzeError(image)
            assertContains(error.detail.orEmpty(), "不得返回毫米测量")
        }
    }

    @Test
    fun `research region rejects inconsistent area and missing bundle provenance`() = runBlocking {
        withResearchService(transform = { response ->
            response.copy(regions = response.regions.map { it.copy(coveragePercent = 80.0) })
        }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "覆盖率与像素面积不一致")
        }
        withResearchService(transform = { response ->
            response.copy(model = response.model.copy(bundleManifestSha256 = null))
        }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "模型包哈希")
        }
    }

    @Test
    fun `raw legacy JSON rejects null or omitted physical size instead of inventing zero`() = runBlocking {
        for (omitSize in listOf(false, true)) {
            withResearchService(transformJson = { response ->
                val model = response.getValue("model").jsonObject.toMutableMap()
                model.remove("intended_use")
                model.remove("bundle_manifest_sha256")
                model.remove("supported_image_types")
                val regions = response.getValue("regions").jsonArray.map { value ->
                    val region = value.jsonObject.toMutableMap()
                    if (omitSize) region.remove("estimated_size_mm") else region["estimated_size_mm"] = JsonNull
                    JsonObject(region)
                }
                JsonObject(response + mapOf("model" to JsonObject(model), "regions" to JsonArray(regions)))
            }) { image ->
                assertContains(analyzeError(image).detail.orEmpty(), "缺少有效物理尺寸")
            }
        }
    }

    @Test
    fun `raw research JSON cannot lose its purpose and fall through to clinical mapping`() = runBlocking {
        withResearchService(transformJson = { response ->
            val model = response.getValue("model").jsonObject.toMutableMap()
            model.remove("intended_use")
            JsonObject(response + ("model" to JsonObject(model)))
        }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "缺少明确科研用途")
        }
    }

    @Test
    fun `unknown model purpose cannot fall through into legacy medical metrics`() = runBlocking {
        withResearchService(transform = { response ->
            response.copy(model = response.model.copy(intendedUse = "RESEARCH"))
        }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "未知模型用途")
        }
    }

    @Test
    fun `research result rejects unsupported modality and mismatched request identity`() = runBlocking {
        withResearchService(transform = { response ->
            response.copy(model = response.model.copy(supportedImageTypes = listOf("CT")))
        }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "仅支持 ULTRASOUND")
        }
        withResearchService(transform = { response -> response.copy(requestId = "wrong") }) { image ->
            assertContains(analyzeError(image).detail.orEmpty(), "请求ID与请求不一致")
        }
    }

    private suspend fun analyzeError(image: Path): ToolErrorResponse {
        val result = MedicalImageAnalyzerTool.execute(
            MedicalImageAnalyzerTool.Args(image.toString(), ImageType.ULTRASOUND, "H-TEST", "P-TEST", "合成测试"),
        )
        val error = toolJson.decodeFromString(ToolErrorResponse.serializer(), result)
        assertEquals("ANALYSIS_FAILED", error.errorCode)
        return error
    }

    private suspend fun withResearchService(
        transform: (SegmentationServiceResponse) -> SegmentationServiceResponse = { it },
        transformJson: (JsonObject) -> JsonObject = { it },
        inspectRequest: (HttpExchange) -> Unit = {},
        block: suspend (Path) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/segment") { exchange ->
            inspectRequest(exchange)
            val request = toolJson.decodeFromString(
                SegmentationServiceRequest.serializer(), exchange.requestBody.bufferedReader().readText(),
            )
            val response = SegmentationServiceResponse(
                requestId = request.requestId,
                status = "completed",
                message = "Synthetic contract fixture only",
                hospitalId = request.hospitalId,
                patientId = request.patientId,
                patientName = request.patientName,
                imageType = request.imageType,
                model = SegmentationModelDescriptor(
                    name = "synthetic-research", version = "1", backend = "torch_unet", device = "cpu",
                    inputHeight = 32, inputWidth = 32, inChannels = 1, outputClasses = 1,
                    weightsLoaded = true, intendedUse = "research",
                    bundleManifestSha256 = "a".repeat(64), supportedImageTypes = listOf("ULTRASOUND"),
                ),
                timing = SegmentationTiming(1, 1, 1),
                qualityGate = SegmentationQualityGate("PASS", "Synthetic only"),
                artifacts = SegmentationArtifacts(),
                regions = listOf(SegmentationRegion(
                    regionId = "test-region", className = "target_region", label = "R1",
                    confidence = 0.9, location = "image centre", severity = "not_assessed",
                    coveragePercent = 1.5625, estimatedSizeMm = null,
                    boundingBox = SegmentationBoundingBox(25.0, 25.0, 12.5, 12.5),
                    pixelCount = 16, widthPx = 4, heightPx = 4,
                )),
            )
            val raw = toolJson.encodeToJsonElement(SegmentationServiceResponse.serializer(), transform(response)).jsonObject
            val bytes = toolJson.encodeToString(JsonObject.serializer(), transformJson(raw)).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val image = Files.createTempFile("research-contract-", ".png")
        ImageIO.write(BufferedImage(32, 32, BufferedImage.TYPE_BYTE_GRAY), "png", image.toFile())
        server.start()
        MedicalImageAnalyzerTool.configureSegmentationService(
            SegmentationServiceSettings(true, "http://127.0.0.1:${server.address.port}", 5),
        )
        try {
            block(image)
        } finally {
            server.stop(0)
            Files.deleteIfExists(image)
            MedicalImageAnalyzerTool.configureSegmentationService(SegmentationServiceSettings.DISABLED)
        }
    }
}
