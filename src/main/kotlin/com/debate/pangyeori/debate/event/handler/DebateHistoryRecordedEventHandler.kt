package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.debate.event.DebateHistoryRecordedEvent
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateHistoryRecordedEventHandler(
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateHistoryRecordedEvent,
    ) {
        runCatching {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = event.debateId,
                    type = if (event.content == null) {
                        DebateConnectionEvent.Type.STATEMENT_TIMED_OUT
                    } else {
                        DebateConnectionEvent.Type.STATEMENT_SUBMITTED
                    },
                    role = event.role,
                    stage = event.stage,
                    content = event.content,
                ),
            )
        }.onFailure {
            logger.warn(it) { "토론 발언 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
