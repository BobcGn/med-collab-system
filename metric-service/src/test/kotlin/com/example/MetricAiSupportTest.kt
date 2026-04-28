package com.example

import dto.MetricAiSocketRequest
import io.ktor.server.config.MapApplicationConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricAiSupportTest {
    @Test
    fun `should prefer environment variable when resolving deepseek settings`() {
        val settings = resolveDeepSeekSettings(
            configApiKey = "config-key",
            envApiKey = "env-key",
            systemPropertyApiKey = "system-key",
            legacySystemPropertyApiKey = "legacy-key",
        )

        assertEquals("env-key", settings.apiKey)
        assertEquals("environment variable DEEPSEEK_API_KEY", settings.source)
    }

    @Test
    fun `should preserve uploaded raster payload for structured pipeline`() {
        val request = MetricAiSocketRequest(
            type = "image",
            imageData = "data:image/png;base64,ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
            imageType = "XRAY",
            patientId = "p-1",
            patientName = "李四",
            hospitalId = "h-1",
            message = "请分析这张影像",
        )

        val agentInput = request.toAgentInput(Json)

        assertContains(agentInput, "data:image/png;base64,ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
    }

    @Test
    fun `should disable segmentation service by default for local metric pipeline`() {
        val settings = resolveSegmentationServiceSettings(MapApplicationConfig())

        assertFalse(settings.enabled)
        assertEquals("http://127.0.0.1:8099", settings.baseUrl)
        assertEquals(30, settings.timeoutSeconds)
    }

    @Test
    fun `should allow explicitly enabling segmentation service`() {
        val settings = resolveSegmentationServiceSettings(
            MapApplicationConfig(
                "segmentation.enabled" to "true",
                "segmentation.baseUrl" to "http://127.0.0.1:19099",
                "segmentation.timeoutSeconds" to "45",
            ),
        )

        assertTrue(settings.enabled)
        assertEquals("http://127.0.0.1:19099", settings.baseUrl)
        assertEquals(45, settings.timeoutSeconds)
    }
}
