package aiagent.tools

import aiagent.strategy.executeControlledMetricReportPipeline
import dto.MetricDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Opt-in local integration; configuration is documented in segmentation-service/README.md. */
class ResearchSegmentationLiveIntegrationTest {
    @AfterTest
    fun reset() {
        MedicalImageAnalyzerTool.configureSegmentationService(SegmentationServiceSettings.DISABLED)
    }

    @Test
    fun `real research service feeds the controlled pipeline and non diagnostic PDF`() = runBlocking {
        val url = System.getenv("METRIC_RESEARCH_TEST_URL")
        val image = System.getenv("METRIC_RESEARCH_TEST_IMAGE")
        assumeTrue("Live integration requires an explicit local URL and image", !url.isNullOrBlank() && !image.isNullOrBlank())
        MedicalImageAnalyzerTool.configureSegmentationService(
            SegmentationServiceSettings(true, requireNotNull(url), 30),
        )
        val result = executeControlledMetricReportPipeline(
            """{
              "imagePath": ${toolJson.encodeToString(String.serializer(), requireNotNull(image))},
              "imageType": "ULTRASOUND", "hospitalId": "RESEARCH-ONLY",
              "patientId": "PUBLIC-DATA-NOT-A-PATIENT", "patientName": "公开数据科研测试"
            }""".trimIndent(),
        )
        val payload = toolJson.decodeFromString(GeneratedReportPayload.serializer(), result)
        val output = System.getenv("METRIC_RESEARCH_TEST_OUTPUT")?.takeIf { it.isNotBlank() }
        try {
            assertEquals("research", payload.intendedUse)
            assertEquals("ULTRASOUND_RESEARCH_REPORT", payload.report.reportType)
            assertEquals("generated", payload.report.status)
            assertEquals(payload.analysis.id, payload.analysisId)
            assertTrue(payload.report.analysisIds.contains(payload.analysis.id))
            assertTrue(payload.highlightRegions.isNotEmpty())
            assertTrue(payload.highlightRegions.all { (it.pixelCount ?: 0) > 0 })
            assertTrue(payload.highlightRegions.all { it.estimatedSizeMm == null && it.severity.isEmpty() })
            val metrics = payload.analysis.metrics as MetricDto.MetricCollection
            assertTrue(metrics.metrics.all { it is MetricDto.GeneralMetric && it.confidence == null })
            assertContains(payload.reportContent, "科研影像分割报告（非诊断）")
            assertFalse(payload.reportContent.contains("报告类型: 正式分析报告"))
            assertFalse(payload.reportContent.contains("0.0 mm"))
            val expectedHash = System.getenv("METRIC_RESEARCH_TEST_BUNDLE_SHA256")
            if (!expectedHash.isNullOrBlank()) {
                assertEquals(expectedHash, payload.keyIndicators.single { it.name == "模型包 SHA-256" }.value)
            }
            val reportPath = Path.of(assertNotNull(payload.report.filePath))
            assertTrue(Files.isRegularFile(reportPath))
            assertTrue(Files.size(reportPath) > 0)
            assertNull(payload.analysis.errorMessage)
            if (output != null) {
                val recordPath = Path.of(output).toAbsolutePath()
                Files.createDirectories(recordPath.parent)
                Files.writeString(recordPath, result)
            }
        } finally {
            if (output == null) {
                payload.report.filePath?.let { Files.deleteIfExists(Path.of(it)) }
            }
        }
    }
}
