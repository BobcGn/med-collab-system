package com.example

import dto.NotificationSocketEnvelope
import dto.SocketUserContext
import dto.SystemNotificationMessage
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json

private data class NotificationConnection(
    val session: DefaultWebSocketServerSession,
    val user: SocketUserContext,
)

object SystemNotificationHub {
    private const val MAX_HISTORY = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = ConcurrentHashMap<String, NotificationConnection>()
    private val history = CopyOnWriteArrayList<SystemNotificationMessage>()

    suspend fun register(
        session: DefaultWebSocketServerSession,
        user: SocketUserContext,
    ): String {
        val connectionId = UUID.randomUUID().toString()
        sessions[connectionId] = NotificationConnection(session = session, user = user)
        return connectionId
    }

    fun unregister(connectionId: String) {
        sessions.remove(connectionId)
    }

    fun snapshot(): List<SystemNotificationMessage> = history.toList()

    suspend fun sendSnapshot(
        session: DefaultWebSocketServerSession,
        user: SocketUserContext,
    ) {
        val payload = NotificationSocketEnvelope(
            type = "snapshot",
            message = "system notification channel ready",
            notifications = snapshot(),
            user = user,
        )
        session.send(Frame.Text(json.encodeToString(payload)))
    }

    suspend fun publish(notification: SystemNotificationMessage) {
        history.add(0, notification)
        while (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }

        broadcast(
            NotificationSocketEnvelope(
                type = "announcement",
                notification = notification,
            )
        )
    }

    suspend fun broadcast(envelope: NotificationSocketEnvelope) {
        val payload = json.encodeToString(envelope)
        val staleConnections = mutableListOf<String>()

        sessions.forEach { (connectionId, connection) ->
            runCatching {
                connection.session.send(Frame.Text(payload))
            }.onFailure {
                staleConnections += connectionId
            }
        }

        staleConnections.forEach(::unregister)
    }
}
