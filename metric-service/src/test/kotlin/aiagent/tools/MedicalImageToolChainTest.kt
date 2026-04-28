package aiagent.tools

import dto.MetricDto
import dto.AnalysisResultDto
import enums.ImageType
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MedicalImageToolChainTest {
    @AfterTest
    fun resetSegmentationService() {
        MedicalImageAnalyzerTool.configureSegmentationService(SegmentationServiceSettings.DISABLED)
    }

    @Test
    fun `should analyze raster image into structured payload`() {
        runBlocking {
            val imagePath = createSampleImage()

            try {
                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = imagePath.toString(),
                        imageType = ImageType.XRAY,
                        hospitalId = "H-001",
                        patientId = "P-001",
                        patientName = "张三",
                    ),
                )

                val payload = toolJson.decodeFromString(
                    MedicalImageAnalysisPayload.serializer(),
                    rawResult,
                )

                assertEquals("PIXEL", payload.analysisMode)
                assertEquals("张三", payload.analysis.patientName)
                assertIs<MetricDto.XRayMetric>(payload.analysis.metrics)
                assertTrue(payload.keyIndicators.isNotEmpty())
                assertTrue(payload.highlightRegions.isNotEmpty())
                assertTrue(payload.highlightLegend.isNotEmpty())
                assertEquals("红色", payload.highlightRegions.first().colorName)
                assertTrue(payload.highlightRegions.first().contour.isNotEmpty())
            } finally {
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should analyze uploaded data url into pixel payload`() {
        runBlocking {
            val imagePath = createSampleImage()

            try {
                val dataUrl = toDataUrl(imagePath)
                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = dataUrl,
                        imageType = ImageType.XRAY,
                        hospitalId = "H-003",
                        patientId = "P-003",
                        patientName = "王五",
                    ),
                )

                val payload = toolJson.decodeFromString(
                    MedicalImageAnalysisPayload.serializer(),
                    rawResult,
                )

                assertEquals("PIXEL", payload.analysisMode)
                assertEquals("DATA_URL", payload.source.sourceType)
                assertEquals("王五", payload.analysis.patientName)
                assertTrue(payload.summary.contains("像素级分析"))
                assertTrue(payload.highlightRegions.isNotEmpty())
            } finally {
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should consume segmentation service result for uploaded data url`() {
        runBlocking {
            val imagePath = createSampleImage()
            val receivedRequestBodies = mutableListOf<String>()
            val server = startFakeSegmentationService(receivedRequestBodies)

            try {
                MedicalImageAnalyzerTool.configureSegmentationService(
                    SegmentationServiceSettings(
                        enabled = true,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        timeoutSeconds = 5,
                    ),
                )

                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = toDataUrl(imagePath),
                        imageType = ImageType.XRAY,
                        hospitalId = "H-SEG",
                        patientId = "P-SEG",
                        patientName = "分割测试",
                    ),
                )

                val payload = toolJson.decodeFromString(
                    MedicalImageAnalysisPayload.serializer(),
                    rawResult,
                )

                assertEquals("PIXEL", payload.analysisMode)
                assertEquals("completed", payload.analysis.status)
                assertTrue(payload.summary.contains("segmentation-service"))
                assertTrue(payload.highlightRegions.isNotEmpty())
                assertTrue(payload.keyIndicators.any { indicator -> indicator.name == "分割模型" })
                assertFalse(payload.recommendations.any { recommendation -> recommendation.contains("/tmp/seg-test") })
                assertEquals(1, receivedRequestBodies.size)
                assertContains(receivedRequestBodies.single(), "\"source_type\": \"DATA_URL\"")
                assertContains(receivedRequestBodies.single(), "\"patient_id\": \"P-SEG\"")
            } finally {
                server.stop(0)
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should use unique segmentation request id for repeated analysis`() {
        runBlocking {
            val imagePath = createSampleImage()
            val dataUrl = toDataUrl(imagePath)
            val receivedRequestBodies = mutableListOf<String>()
            val server = startFakeSegmentationService(receivedRequestBodies)

            try {
                MedicalImageAnalyzerTool.configureSegmentationService(
                    SegmentationServiceSettings(
                        enabled = true,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        timeoutSeconds = 5,
                    ),
                )

                repeat(2) {
                    MedicalImageAnalyzerTool.execute(
                        MedicalImageAnalyzerTool.Args(
                            imagePath = dataUrl,
                            imageType = ImageType.XRAY,
                            hospitalId = "H-SEG",
                            patientId = "P-SEG",
                            patientName = "分割测试",
                        ),
                    )
                }

                val requestIds = receivedRequestBodies.map(::extractRequestId)
                assertEquals(2, requestIds.size)
                assertNotEquals(requestIds[0], requestIds[1])
            } finally {
                server.stop(0)
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should reject limited segmentation quality gate before formal report`() {
        runBlocking {
            val imagePath = createSampleImage()
            val server = startFakeSegmentationService(
                receivedRequestBodies = mutableListOf(),
                serviceStatus = "limited",
                qualityGateStatus = "LIMITED",
            )

            try {
                MedicalImageAnalyzerTool.configureSegmentationService(
                    SegmentationServiceSettings(
                        enabled = true,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        timeoutSeconds = 5,
                    ),
                )

                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = toDataUrl(imagePath),
                        imageType = ImageType.XRAY,
                        hospitalId = "H-SEG",
                        patientId = "P-SEG",
                        patientName = "分割测试",
                    ),
                )
                val payload = toolJson.decodeFromString(ToolErrorResponse.serializer(), rawResult)

                assertEquals("ANALYSIS_FAILED", payload.errorCode)
                assertContains(payload.detail.orEmpty(), "分割服务质量门禁未通过")
            } finally {
                server.stop(0)
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should reject unloaded segmentation weights before formal report`() {
        runBlocking {
            val imagePath = createSampleImage()
            val server = startFakeSegmentationService(
                receivedRequestBodies = mutableListOf(),
                weightsLoaded = false,
            )

            try {
                MedicalImageAnalyzerTool.configureSegmentationService(
                    SegmentationServiceSettings(
                        enabled = true,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        timeoutSeconds = 5,
                    ),
                )

                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = toDataUrl(imagePath),
                        imageType = ImageType.XRAY,
                        hospitalId = "H-SEG",
                        patientId = "P-SEG",
                        patientName = "分割测试",
                    ),
                )
                val payload = toolJson.decodeFromString(ToolErrorResponse.serializer(), rawResult)

                assertEquals("ANALYSIS_FAILED", payload.errorCode)
                assertContains(payload.detail.orEmpty(), "未加载训练权重")
            } finally {
                server.stop(0)
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should reject mock segmentation backend before formal report`() {
        runBlocking {
            val imagePath = createSampleImage()
            val server = startFakeSegmentationService(
                receivedRequestBodies = mutableListOf(),
                backend = "mock",
            )

            try {
                MedicalImageAnalyzerTool.configureSegmentationService(
                    SegmentationServiceSettings(
                        enabled = true,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        timeoutSeconds = 5,
                    ),
                )

                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = toDataUrl(imagePath),
                        imageType = ImageType.XRAY,
                        hospitalId = "H-SEG",
                        patientId = "P-SEG",
                        patientName = "分割测试",
                    ),
                )
                val payload = toolJson.decodeFromString(ToolErrorResponse.serializer(), rawResult)

                assertEquals("ANALYSIS_FAILED", payload.errorCode)
                assertContains(payload.detail.orEmpty(), "mock 后端")
            } finally {
                server.stop(0)
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should generate markdown report from analysis payload`() {
        runBlocking {
            val imagePath = createSampleImage()
            var reportPath: Path? = null

            try {
                val analysisResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = imagePath.toString(),
                        imageType = ImageType.CT,
                        hospitalId = "H-002",
                        patientId = "P-002",
                        patientName = "李四",
                    ),
                )

                val rawReport = ReportGenerateTool.execute(
                    ReportGenerateTool.Args(
                        analysisResult = analysisResult,
                    ),
                )

                val payload = toolJson.decodeFromString(
                    GeneratedReportPayload.serializer(),
                    rawReport,
                )

                reportPath = Path.of(payload.report.filePath!!)
                assertEquals("generated", payload.report.status)
                assertTrue(Files.exists(reportPath))
                assertTrue(reportPath.fileName.toString().endsWith(".pdf"))
                val headerBytes = Files.readAllBytes(reportPath).copyOfRange(0, 5)
                assertEquals("%PDF-", String(headerBytes, StandardCharsets.ISO_8859_1))
                assertContains(payload.reportContent, "李四")
                assertContains(payload.reportContent, "关键指标")
                assertContains(payload.reportContent, "影像高亮附图")
                assertContains(payload.reportContent, "病灶高亮说明")
                assertContains(payload.reportContent, "高亮图例")
                assertContains(payload.reportContent, "详细信息")
                assertTrue(payload.highlightRegions.isNotEmpty())
                assertTrue(payload.highlightLegend.isNotEmpty())
                assertTrue(payload.highlightRegions.first().contour.isNotEmpty())
            } finally {
                Files.deleteIfExists(imagePath)
                reportPath?.let { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun `should reject unknown modality for official analysis`() {
        runBlocking {
            val imagePath = createSampleImage()

            try {
                val rawResult = MedicalImageAnalyzerTool.execute(
                    MedicalImageAnalyzerTool.Args(
                        imagePath = imagePath.toString(),
                        imageType = ImageType.OTHER,
                        hospitalId = "H-004",
                        patientId = "P-004",
                        patientName = "赵六",
                    ),
                )

                val payload = toolJson.decodeFromString(
                    ToolErrorResponse.serializer(),
                    rawResult,
                )

                assertEquals("ANALYSIS_FAILED", payload.errorCode)
                assertContains(payload.detail.orEmpty(), "未知影像模态不允许进入正式结构化分析")
            } finally {
                Files.deleteIfExists(imagePath)
            }
        }
    }

    @Test
    fun `should reject legacy analysis payload when generating formal report`() {
        runBlocking {
            val legacyAnalysis = AnalysisResultDto.AnalysisResultComplete(
                id = "ANL-LEGACY-001",
                hospitalId = "H-005",
                imageId = "IMG-LEGACY-001",
                patientId = "P-005",
                patientName = "钱七",
                metrics = MetricDto.XRayMetric(
                    opacity = "斑片状高密度影",
                    size = 12.5,
                    location = "右上肺",
                    boneStructure = "未见明显异常",
                    confidence = 0.92,
                ),
                status = "completed",
                createdAt = "2026-04-19T10:00:00",
                completedAt = "2026-04-19T10:00:01",
                errorMessage = null,
            )

            val rawReport = ReportGenerateTool.execute(
                ReportGenerateTool.Args(
                    analysisResult = toolJson.encodeToString(
                        AnalysisResultDto.AnalysisResultComplete.serializer(),
                        legacyAnalysis,
                    ),
                ),
            )

            val payload = toolJson.decodeFromString(
                ToolErrorResponse.serializer(),
                rawReport,
            )

            assertEquals("REPORT_GENERATION_FAILED", payload.errorCode)
            assertContains(payload.detail.orEmpty(), "正式报告仅接受经过校验的结构化影像分析结果")
        }
    }

    private fun createSampleImage(): Path {
        val image = BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = if (x in 24..68 && y in 18..60) {
                    Color(230, 230, 230)
                } else {
                    Color(25, 25, 25)
                }
                image.setRGB(x, y, color.rgb)
            }
        }

        val path = Files.createTempFile("metric-ai-tool-", ".png")
        ImageIO.write(image, "png", path.toFile())
        return path
    }

    private fun toDataUrl(path: Path): String {
        val bytes = Files.readAllBytes(path)
        return "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun startFakeSegmentationService(
        receivedRequestBodies: MutableList<String>,
        serviceStatus: String = "completed",
        qualityGateStatus: String = "PASS",
        weightsLoaded: Boolean = true,
        backend: String = "torch_unet",
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/segment") { exchange ->
            val requestBody = exchange.requestBody.use { inputStream ->
                String(inputStream.readBytes(), UTF_8)
            }
            receivedRequestBodies += requestBody
            val responseBody = fakeSegmentationResponse(
                requestBody = requestBody,
                serviceStatus = serviceStatus,
                qualityGateStatus = qualityGateStatus,
                weightsLoaded = weightsLoaded,
                backend = backend,
            ).toByteArray(UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBody.size.toLong())
            exchange.responseBody.use { outputStream ->
                outputStream.write(responseBody)
            }
        }
        server.start()
        return server
    }

    private fun fakeSegmentationResponse(
        requestBody: String,
        serviceStatus: String,
        qualityGateStatus: String,
        weightsLoaded: Boolean,
        backend: String,
    ): String {
        val requestId = extractRequestId(requestBody)
        return """
            {
              "request_id": "$requestId",
              "status": "$serviceStatus",
              "message": "Segmentation completed: test quality gate passed.",
              "hospital_id": "H-SEG",
              "patient_id": "P-SEG",
              "patient_name": "分割测试",
              "image_type": "XRAY",
              "model": {
                "name": "test-unet",
                "version": "1.0",
                "backend": "$backend",
                "device": "cpu",
                "input_height": 256,
                "input_width": 256,
                "in_channels": 1,
                "output_classes": 1,
                "weights_path": null,
                "weights_loaded": $weightsLoaded
              },
              "timing": {
                "preprocess_ms": 4,
                "inference_ms": 8,
                "postprocess_ms": 3
              },
              "quality_gate": {
                "status": "$qualityGateStatus",
                "reason": "Segmentation passed contract test"
              },
              "artifacts": {
                "mask_path": "/tmp/seg-test/mask.png",
                "overlay_path": "/tmp/seg-test/overlay.png",
                "metadata_path": "/tmp/seg-test/result.json"
              },
              "regions": [
                {
                  "region_id": "region-1",
                  "class_name": "lesion",
                  "label": "L1",
                  "confidence": 0.91,
                  "location": "右上象限",
                  "severity": "中风险",
                  "coverage_percent": 12.5,
                  "estimated_size_mm": 18.4,
                  "bounding_box": {
                    "left_percent": 22.0,
                    "top_percent": 18.0,
                    "width_percent": 34.0,
                    "height_percent": 28.0
                  },
                  "contour": [
                    {"x_percent": 22.0, "y_percent": 18.0},
                    {"x_percent": 56.0, "y_percent": 18.0},
                    {"x_percent": 56.0, "y_percent": 46.0},
                    {"x_percent": 22.0, "y_percent": 46.0}
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun extractRequestId(requestBody: String): String {
        return Regex(""""request_id"\s*:\s*"([^"]+)"""")
            .find(requestBody)
            ?.groupValues
            ?.getOrNull(1)
            ?: ""
    }
}
