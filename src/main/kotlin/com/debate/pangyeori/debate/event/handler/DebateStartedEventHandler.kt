package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.debate.event.DebateStartedEvent
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateStartedEventHandler(
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateStartedEvent,
    ) {
        runCatching {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = event.debateId,
                    type = DebateConnectionEvent.Type.STARTED,
                ),
            )
        }.onFailure {
            logger.warn(it) { "토론 시작 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
