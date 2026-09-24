package com.debate.pangyeori.notification.repository

import com.debate.pangyeori.notification.domain.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface NotificationRepository : JpaRepository<Notification, String> {
    @Query(
        """
        SELECT n FROM Notification n
        WHERE n.recipient.id = :recipientId
        AND (:cursor IS NULL OR n.id < :cursor)
        ORDER BY n.id DESC
        """
    )
    fun findAllByRecipientId(
        recipientId: String,
        cursor: String?,
        pageable: Pageable,
    ): List<Notification>

    fun findByIdAndRecipientId(
        id: String,
        recipientId: String,
    ): Notification?

    fun findAllByRecipientIdAndReadAtIsNull(
        recipientId: String,
    ): List<Notification>

    fun countByRecipientIdAndReadAtIsNull(
        recipientId: String,
    ): Long
}
