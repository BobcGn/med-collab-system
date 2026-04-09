package dto

import kotlinx.serialization.Serializable

@Serializable
data class SocketUserContext(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val role: String? = null,
)

@Serializable
data class SystemNotificationMessage(
    val id: String,
    val title: String,
    val content: String,
    val priority: String = "normal",
    val createdAt: String,
    val senderId: String,
    val senderName: String,
)

@Serializable
data class NotificationSocketCommand(
    val type: String,
    val title: String? = null,
    val content: String? = null,
    val priority: String? = null,
)

@Serializable
data class NotificationSocketEnvelope(
    val type: String,
    val message: String? = null,
    val notification: SystemNotificationMessage? = null,
    val notifications: List<SystemNotificationMessage> = emptyList(),
    val user: SocketUserContext? = null,
)

@Serializable
data class MetricAiSocketRequest(
    val type: String = "chat",
    val requestId: String? = null,
    val message: String? = null,
    val imageData: String? = null,
    val imageType: String? = null,
    val patientId: String? = null,
    val patientName: String? = null,
    val hospitalId: String? = null,
)

@Serializable
data class MetricAiSocketEnvelope(
    val type: String,
    val requestId: String? = null,
    val message: String? = null,
    val analysisResult: String? = null,
    val confidence: Int? = null,
    val createdAt: String,
    val user: SocketUserContext? = null,
)
