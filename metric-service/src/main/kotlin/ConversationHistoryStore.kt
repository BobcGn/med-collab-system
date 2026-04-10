package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime

private val conversationJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
data class ConversationHistoryDocument(
    val conversationId: String,
    val patientName: String,
    val hospitalId: String,
    val confirmedResultMessageId: String? = null,
    val messages: List<JsonElement> = emptyList(),
    val updatedAt: String,
)

class ConversationHistoryStore(
    private val conversationDirectory: Path = resolveProjectSubdirectory("chat-histories"),
) {
    fun loadConversation(conversationId: String, hospitalId: String): ConversationHistoryDocument? {
        val filePath = resolveConversationPath(conversationId, hospitalId)
        if (!Files.exists(filePath)) {
            return null
        }

        val json = Files.readString(filePath, StandardCharsets.UTF_8)
        return conversationJson.decodeFromString(ConversationHistoryDocument.serializer(), json)
    }

    fun saveConversation(document: ConversationHistoryDocument): ConversationHistoryDocument {
        val normalized = document.copy(
            conversationId = document.conversationId.trim(),
            patientName = document.patientName.trim(),
            hospitalId = document.hospitalId.trim().ifEmpty { "unknown-hospital" },
            updatedAt = document.updatedAt.trim().ifEmpty { LocalDateTime.now().toString() },
        )
        val filePath = resolveConversationPath(normalized.conversationId, normalized.hospitalId)
        Files.createDirectories(filePath.parent)
        Files.writeString(
            filePath,
            conversationJson.encodeToString(normalized),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return normalized
    }

    fun deleteConversation(conversationId: String, hospitalId: String): Boolean {
        return Files.deleteIfExists(resolveConversationPath(conversationId, hospitalId))
    }

    private fun resolveConversationPath(conversationId: String, hospitalId: String): Path {
        val safeHospitalId = sanitizeFileComponent(hospitalId, fallback = "unknown_hospital")
        val safeConversationId = sanitizeFileComponent(conversationId, fallback = "conversation")
        return conversationDirectory.resolve("${safeHospitalId}_${safeConversationId}.json")
    }
}
