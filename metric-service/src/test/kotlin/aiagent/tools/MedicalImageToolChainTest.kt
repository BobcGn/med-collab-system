package aiagent.tools

import dto.MetricDto
import enums.ImageType
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MedicalImageToolChainTest {
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
}
