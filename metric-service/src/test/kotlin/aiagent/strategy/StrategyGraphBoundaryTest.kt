package aiagent.strategy

import aiagent.tools.GeneratedReportPayload
import aiagent.tools.toolJson
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyGraphBoundaryTest {
    @Test
    fun `should reject unknown modality before formal analysis stage`() {
        val error = runBlocking {
            assertFailsWith<IllegalArgumentException> {
                executeControlledMetricReportPipeline(
                    """
                    {
                      "imagePath": "/tmp/example.png",
                      "imageType": "OTHER",
                      "hospitalId": "H-010",
                      "patientId": "P-010",
                      "patientName": "测试患者",
                      "message": "请生成正式结果"
                    }
                    """.trimIndent(),
                )
            }
        }

        assertContains(error.message.orEmpty(), "当前仅支持明确模态的医疗影像分析请求")
    }

    @Test
    fun `should reject metadata only input before formal report stage`() {
        val error = runBlocking {
            assertFailsWith<IllegalArgumentException> {
                executeControlledMetricReportPipeline(
                    """
                    {
                      "imagePath": "inline-image://image/png?size=344100B",
                      "imageType": "XRAY",
                      "hospitalId": "H-011",
                      "patientId": "P-011",
                      "patientName": "测试患者"
                    }
                    """.trimIndent(),
                )
            }
        }

        assertContains(error.message.orEmpty(), "正式报告仅接受像素级结构化分析结果")
    }

    @Test
    fun `should complete controlled pipeline for uploaded raster data`() {
        runBlocking {
            val imagePath = createSampleImage()
            var reportPath: Path? = null

            try {
                val result = executeControlledMetricReportPipeline(
                    """
                    {
                      "imagePath": "${toDataUrl(imagePath)}",
                      "imageType": "XRAY",
                      "hospitalId": "H-012",
                      "patientId": "P-012",
                      "patientName": "像素测试患者"
                    }
                    """.trimIndent(),
                )

                val payload = toolJson.decodeFromString(
                    GeneratedReportPayload.serializer(),
                    result,
                )

                reportPath = payload.report.filePath?.let(Path::of)
                assertEquals("generated", payload.report.status)
                assertEquals("PIXEL", payload.analysisMode)
                assertTrue(reportPath != null && Files.exists(reportPath))
                assertContains(payload.reportContent, "正式分析报告")
                assertContains(payload.reportContent, "图像尺寸")
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

        val path = Files.createTempFile("metric-ai-strategy-", ".png")
        ImageIO.write(image, "png", path.toFile())
        return path
    }

    private fun toDataUrl(path: Path): String {
        val bytes = Files.readAllBytes(path)
        return "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }
}
