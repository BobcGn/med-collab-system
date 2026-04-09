package com.example

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
    val normalized = imageData?.trim().orEmpty()
    if (normalized.isBlank()) {
        return ""
    }

    if (normalized.startsWith("data:", ignoreCase = true)) {
        return buildInlineImageReference(normalized)
    }

    return if (normalized.length > 240) {
        normalized.take(240) + "...(truncated)"
    } else {
        normalized
    }
}

private fun buildInlineImageReference(dataUrl: String): String {
    val header = dataUrl.substringBefore(',')
    val payload = dataUrl.substringAfter(',', "")
    val mimeType = header
        .substringAfter("data:", "")
        .substringBefore(';')
        .ifBlank { "application/octet-stream" }
    val approximateBytes = if (payload.isBlank()) 0 else (payload.length * 3L) / 4L

    return "inline-image://$mimeType?size=${approximateBytes}B"
}
