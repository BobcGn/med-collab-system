package aiagent.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricAiConversationGuardTest {
    @Test
    fun `should allow medical image metric discussion without image`() {
        val scope = determineMetricAiConversationScope(
            message = "这个CT报告里的HU值和结节直径怎么解读？",
            hasImage = false,
        )

        assertEquals(MetricAiConversationScope.METRIC_DISCUSSION, scope)
    }

    @Test
    fun `should reject unsupported chit chat without image`() {
        val scope = determineMetricAiConversationScope(
            message = "你好，顺便讲个笑话吧",
            hasImage = false,
        )

        assertEquals(MetricAiConversationScope.UNSUPPORTED, scope)
    }

    @Test
    fun `should prioritize image analysis when image is uploaded`() {
        val scope = determineMetricAiConversationScope(
            message = "顺便讲个笑话吧",
            hasImage = true,
        )

        assertEquals(MetricAiConversationScope.IMAGE_ANALYSIS, scope)
    }
}
