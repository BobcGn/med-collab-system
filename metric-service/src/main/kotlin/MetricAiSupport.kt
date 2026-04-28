package com.example

import aiagent.tools.SegmentationServiceSettings
import dto.MetricAiSocketRequest
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.propertyOrNull
import kotlinx.serialization.json.Json

internal data class DeepSeekSettings(
    val apiKey: String?,
    val source: String,
) {
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank()

    fun unavailableReason(): String {
        return "DeepSeek API key 未配置。请设置 DEEPSEEK_API_KEY、-Ddeepseek.apiKey，或在 application.conf 中提供 deepseek.apiKey。"
    }
}

internal fun resolveDeepSeekSettings(config: ApplicationConfig): DeepSeekSettings {
    return resolveDeepSeekSettings(
        configApiKey = config.propertyOrNull("deepseek.apiKey")?.getString(),
    )
}

internal fun resolveDeepSeekSettings(
    configApiKey: String? = null,
    envApiKey: String? = System.getenv("DEEPSEEK_API_KEY"),
    systemPropertyApiKey: String? = System.getProperty("deepseek.apiKey"),
    legacySystemPropertyApiKey: String? = System.getProperty("deepseek.api-key"),
): DeepSeekSettings {
    val candidates = listOf(
        "environment variable DEEPSEEK_API_KEY" to envApiKey,
        "system property deepseek.apiKey" to systemPropertyApiKey,
        "system property deepseek.api-key" to legacySystemPropertyApiKey,
        "application config deepseek.apiKey" to configApiKey,
    )

    val resolved = candidates.firstNotNullOfOrNull { (source, value) ->
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { source to it }
    }

    return if (resolved != null) {
        DeepSeekSettings(
            apiKey = resolved.second,
            source = resolved.first,
        )
    } else {
        DeepSeekSettings(
            apiKey = null,
            source = "missing",
        )
    }
}

internal fun MetricAiSocketRequest.toAgentInput(json: Json): String {
    return """{
        "imagePath": ${json.encodeToString(buildAgentImageReference(imageData))},
        "imageType": ${json.encodeToString(imageType.orEmpty())},
        "hospitalId": ${json.encodeToString(hospitalId.orEmpty())},
        "patientId": ${json.encodeToString(patientId.orEmpty())},
        "patientName": ${json.encodeToString(patientName.orEmpty())},
        "message": ${json.encodeToString(message.orEmpty())}
    }""".trimIndent()
}

internal fun buildAgentImageReference(imageData: String?): String {
    return imageData?.trim().orEmpty()
}

internal fun resolveSegmentationServiceSettings(config: ApplicationConfig): SegmentationServiceSettings {
    return SegmentationServiceSettings(
        enabled = parseBooleanConfig(
            value = config.propertyOrNull("segmentation.enabled")?.getString(),
            defaultValue = false,
        ),
        baseUrl = config.propertyOrNull("segmentation.baseUrl")
            ?.getString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: SegmentationServiceSettings.DISABLED.baseUrl,
        timeoutSeconds = config.propertyOrNull("segmentation.timeoutSeconds")
            ?.getString()
            ?.trim()
            ?.toLongOrNull()
            ?.coerceAtLeast(1)
            ?: SegmentationServiceSettings.DISABLED.timeoutSeconds,
    )
}

private fun parseBooleanConfig(value: String?, defaultValue: Boolean): Boolean {
    return when (value?.trim()?.lowercase()) {
        null, "" -> defaultValue
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> defaultValue
    }
}
