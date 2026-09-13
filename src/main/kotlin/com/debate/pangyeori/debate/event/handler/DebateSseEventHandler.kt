package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.config.AsyncConfig
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.event.DebateGuestStatusChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.stream.message.*
import com.debate.pangyeori.debate.stream.publisher.DebateStreamPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateSseEventHandler(
    private val debateStreamPublisher: DebateStreamPublisher,
    private val debateParticipationService: DebateParticipationService,
) {
    private val logger = KotlinLogging.logger {}

    @Async(AsyncConfig.DEBATE_SSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleStatusChanged(
        event: DebateStatusChangedEvent,
    ) {
        runCatching {
            debateStreamPublisher.publish(
                DebateStreamMessage(
                    debateId = event.debateId,
                    target = DebateStreamMessage.Target.all(),
                    eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                    data = DebateStatusChangedPayload(
                        debateStatus = event.status,
                    ),
                    close = event.status != DebateStatus.WAITING,
                ),
            )
        }.onFailure {
            logger.warn(it) { "토론방 상태 변경 SSE 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }

    @Async(AsyncConfig.DEBATE_SSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleQueueChanged(
        event: DebateQueueChangedEvent,
    ) {
        runCatching {
            debateStreamPublisher.publish(
                DebateStreamMessage(
                    debateId = event.debateId,
                    target = DebateStreamMessage.Target.host(),
                    eventName = DebateStreamEvents.QUEUE_CHANGED,
                    data = QueueChangedPayload(
                        requestList = debateParticipationService.getPendingRequestList(
                            debateId = event.debateId,
                        ),
                    ),
                ),
            )
        }.onFailure {
            logger.warn(it) { "토론방 대기열 변경 SSE 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }

    @Async(AsyncConfig.DEBATE_SSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGuestStatusChanged(
        event: DebateGuestStatusChangedEvent,
    ) {
        runCatching {
            debateStreamPublisher.publish(
                DebateStreamMessage(
                    debateId = event.debateId,
                    target = DebateStreamMessage.Target.user(
                        userId = event.userId,
                    ),
                    eventName = DebateStreamEvents.GUEST_STATUS_CHANGED,
                    data = GuestStatusChangedPayload(
                        guestStatus = event.status,
                    ),
                ),
            )
        }.onFailure {
            logger.warn(it) { "게스트 상태 변경 SSE 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
