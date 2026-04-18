package aiagent.strategy

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

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
}
