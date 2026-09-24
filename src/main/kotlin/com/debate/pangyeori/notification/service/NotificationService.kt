package com.debate.pangyeori.notification.service

import com.debate.pangyeori.common.dto.CursorPage
import com.debate.pangyeori.notification.dto.response.NotificationListResponse
import com.debate.pangyeori.notification.dto.response.NotificationUnreadCountResponse
import com.debate.pangyeori.notification.exception.NotificationNotFoundException
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getMyNotifications(
        userEmail: String,
        cursor: String?,
        pageSize: Int?,
    ): CursorPage<NotificationListResponse> {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val limit = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val notifications = notificationRepository.findAllByRecipientId(
            recipientId = user.id!!,
            cursor = cursor,
            pageable = PageRequest.of(0, limit + 1),
        )
        val hasNext = notifications.size > limit
        val items = notifications.take(limit).map {
            NotificationListResponse.from(
                notification = it,
            )
        }

        return CursorPage(
            items = items,
            nextCursor = if (hasNext) items.last().notificationId else null,
            hasNext = hasNext,
        )
    }

    @Transactional
    fun markAsRead(
        userEmail: String,
        notificationId: String,
    ) {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val notification = notificationRepository.findByIdAndRecipientId(
            id = notificationId,
            recipientId = user.id!!,
        ) ?: throw NotificationNotFoundException()

        notification.read()
    }

    @Transactional
    fun markAllAsRead(
        userEmail: String,
    ) {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()

        notificationRepository.findAllByRecipientIdAndReadAtIsNull(
            recipientId = user.id!!,
        ).forEach {
            it.read()
        }
    }

    @Transactional
    fun deleteNotification(
        userEmail: String,
        notificationId: String,
    ) {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val notification = notificationRepository.findByIdAndRecipientId(
            id = notificationId,
            recipientId = user.id!!,
        ) ?: throw NotificationNotFoundException()

        notificationRepository.delete(notification)
    }

    @Transactional(readOnly = true)
    fun getUnreadCount(
        userEmail: String,
    ): NotificationUnreadCountResponse {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()

        return NotificationUnreadCountResponse(
            count = notificationRepository.countByRecipientIdAndReadAtIsNull(
                recipientId = user.id!!,
            ),
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 50
    }
}
