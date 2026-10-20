package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.event.DebateVerdictIssuedEvent
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase

@Component
class DebateVerdictIssuedEventHandler(
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateVerdictIssuedEvent,
    ) {
        runCatching {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = event.debateId,
                    type = DebateConnectionEvent.Type.VERDICT_ISSUED,
                    stage = event.stage,
                    verdictStatus = event.status,
                    winnerPosition = event.winnerPosition,
                    reasoning = event.reasoning,
                    participantProsScore = event.participantProsScore,
                    participantConsScore = event.participantConsScore,
                ),
            )
        }.onFailure {
            logger.warn(it) { "AI 판결 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
        }

        if (event.stage == DebateStage.FINAL_VERDICT) {
            runCatching {
                debateConnectionEventPublisher.publish(
                    DebateConnectionEvent(
                        debateId = event.debateId,
                        type = DebateConnectionEvent.Type.DEBATE_ENDED,
                        stage = event.stage,
                        verdictStatus = event.status,
                        winnerPosition = event.winnerPosition,
                    ),
                )
            }.onFailure {
                logger.warn(it) { "토론 종료 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
            }
        }
    }
}
