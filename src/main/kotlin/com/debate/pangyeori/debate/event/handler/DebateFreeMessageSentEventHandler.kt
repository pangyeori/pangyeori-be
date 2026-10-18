package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.debate.event.DebateFreeMessageSentEvent
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateFreeMessageSentEventHandler(
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateFreeMessageSentEvent,
    ) {
        runCatching {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = event.debateId,
                    type = DebateConnectionEvent.Type.FREE_MESSAGE_SENT,
                    role = event.role,
                    content = event.content,
                    submittedAt = event.createdAt,
                    messageId = event.messageId,
                ),
            )
        }.onFailure {
            logger.warn(it) { "자유 토론 메시지 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
