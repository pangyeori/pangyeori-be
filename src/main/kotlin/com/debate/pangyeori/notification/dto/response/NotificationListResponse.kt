package com.debate.pangyeori.notification.dto.response

import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import java.time.LocalDateTime

data class NotificationListResponse(
    val notificationId: String,
    val debateId: String,
    val type: NotificationType,
    val message: String,
    val isRead: Boolean,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            notification: Notification,
        ) = NotificationListResponse(
            notificationId = notification.id!!,
            debateId = notification.debate.id!!,
            type = notification.type,
            message = notification.message,
            isRead = notification.isRead,
            createdAt = notification.createdAt!!,
        )
    }
}
