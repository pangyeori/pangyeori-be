package com.debate.pangyeori.notification.event.handler

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateGuestStatusChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.notification.message.NotificationMessages
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.user.domain.User
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationEventHandler(
    private val notificationRepository: NotificationRepository,
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleGuestStatusChanged(
        event: DebateGuestStatusChangedEvent,
    ) {
        val type = when (event.status) {
            DebateUserStatus.ACCEPTED -> NotificationType.QUEUE_REQUEST_ACCEPTED
            DebateUserStatus.REJECTED -> NotificationType.QUEUE_REQUEST_REJECTED
            else -> return
        }
        runCatching {
            val debate = debateRepository.findById(event.debateId).orElse(null) ?: return@runCatching
            val requester = debateUserRepository.findByDebateIdAndUserId(
                debateId = event.debateId,
                userId = event.userId,
            ) ?: return@runCatching

            notify(
                recipient = requester.user,
                debate = debate,
                type = type,
            )
        }.onFailure {
            logger.warn(it) { "참여 요청 결과 알림 생성에 실패했습니다. debateId=${event.debateId}" }
        }
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
        runCatching {
            val debate = debateRepository.findById(event.debateId).orElse(null) ?: return@runCatching
            val requester = debateUserRepository.findByDebateIdAndUserId(
                debateId = event.debateId,
                userId = event.userId!!,
            ) ?: return@runCatching

            notify(
                recipient = debate.host,
                debate = debate,
                type = type,
                requesterNickname = requester.user.nickname,
            )
        }.onFailure {
            logger.warn(it) { "대기열 변경 알림 생성에 실패했습니다. debateId=${event.debateId}" }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handleStatusChanged(
        event: DebateStatusChangedEvent,
    ) {
        if (event.status != DebateStatus.CANCELLED) return

        runCatching {
            val debate = debateRepository.findById(event.debateId).orElse(null) ?: return@runCatching
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
        }.onFailure {
            logger.warn(it) { "토론 취소 알림 생성에 실패했습니다. debateId=${event.debateId}" }
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
