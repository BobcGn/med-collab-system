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
    private val userIdIndex = ConcurrentHashMap<String, MutableSet<String>>()

    suspend fun register(
        session: DefaultWebSocketServerSession,
        user: SocketUserContext,
    ): String {
        val connectionId = UUID.randomUUID().toString()
        sessions[connectionId] = NotificationConnection(session = session, user = user)
        userIdIndex.getOrPut(user.userId) { mutableSetOf() }.add(connectionId)
        return connectionId
    }

    fun unregister(connectionId: String) {
        val connection = sessions.remove(connectionId)
        if (connection != null) {
            val userConnections = userIdIndex[connection.user.userId]
            userConnections?.remove(connectionId)
            if (userConnections.isNullOrEmpty()) {
                userIdIndex.remove(connection.user.userId)
            }
        }
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

    /**
     * 向指定用户发送通知
     */
    suspend fun sendToUser(targetUserId: String, envelope: NotificationSocketEnvelope) {
        val payload = json.encodeToString(envelope)
        val userConnections = userIdIndex[targetUserId] ?: return
        val staleConnections = mutableListOf<String>()

        userConnections.forEach { connectionId ->
            val connection = sessions[connectionId] ?: return@forEach
            runCatching {
                connection.session.send(Frame.Text(payload))
            }.onFailure {
                staleConnections += connectionId
            }
        }

        staleConnections.forEach { connId ->
            val conn = sessions.remove(connId)
            if (conn != null) {
                userIdIndex[conn.user.userId]?.remove(connId)
            }
        }
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
