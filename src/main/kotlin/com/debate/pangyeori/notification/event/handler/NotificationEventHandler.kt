package com.debate.pangyeori.notification.event.handler

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateGuestStatusChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.DebateNotFoundException
import com.debate.pangyeori.debate.exception.UserNotFoundInQueueException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.notification.message.NotificationMessages
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.user.domain.User
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationEventHandler(
    private val notificationRepository: NotificationRepository,
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleGuestStatusChanged(
        event: DebateGuestStatusChangedEvent,
    ) {
        val type = when (event.status) {
            DebateUserStatus.ACCEPTED -> NotificationType.QUEUE_REQUEST_ACCEPTED
            DebateUserStatus.REJECTED -> NotificationType.QUEUE_REQUEST_REJECTED
            else -> return
        }
        val debate = debateRepository.findById(event.debateId).orElse(null) ?: throw DebateNotFoundException()
        val requester = debateUserRepository.findByDebateIdAndUserId(
            debateId = event.debateId,
            userId = event.userId,
        ) ?: throw UserNotFoundInQueueException()

        notify(
            recipient = requester.user,
            debate = debate,
            type = type,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleQueueChanged(
        event: DebateQueueChangedEvent,
    ) {
        val type = when (event.operation) {
            DebateQueueOperation.ADD -> NotificationType.QUEUE_REQUEST_ADDED
            DebateQueueOperation.REMOVE -> NotificationType.QUEUE_REQUEST_REMOVED
            DebateQueueOperation.CLEAR -> return
        }
        val debate = debateRepository.findById(event.debateId).orElse(null) ?: throw DebateNotFoundException()
        val requester = debateUserRepository.findByDebateIdAndUserId(
            debateId = event.debateId,
            userId = event.userId!!,
        ) ?: throw UserNotFoundInQueueException()

        notify(
            recipient = debate.host,
            debate = debate,
            type = type,
            requesterNickname = requester.user.nickname,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleStatusChanged(
        event: DebateStatusChangedEvent,
    ) {
        if (event.status != DebateStatus.CANCELLED) return

        val debate = debateRepository.findById(event.debateId).orElse(null) ?: throw DebateNotFoundException()
        val recipients = debate.guest?.let { listOf(it) } ?: debateUserRepository.findAllByDebateIdAndStatus(
            debateId = event.debateId,
            status = DebateUserStatus.PENDING,
        ).map { it.user }

        recipients.forEach { recipient ->
            notify(
                recipient = recipient,
                debate = debate,
                type = NotificationType.DEBATE_CANCELLED,
            )
        }
    }

    private fun notify(
        recipient: User,
        debate: Debate,
        type: NotificationType,
        requesterNickname: String? = null,
    ) {
        notificationRepository.save(
            Notification.create(
                recipient = recipient,
                debate = debate,
                type = type,
                message = NotificationMessages.build(
                    type = type,
                    debate = debate,
                    requesterNickname = requesterNickname,
                ),
            ),
        )
    }
}
