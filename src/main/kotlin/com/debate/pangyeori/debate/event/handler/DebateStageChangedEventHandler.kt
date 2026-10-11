package com.debate.pangyeori.debate.event.handler

import com.debate.pangyeori.config.SchedulingConfig
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.service.DebateHistoryService
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZoneOffset

@Component
class DebateStageChangedEventHandler(
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
    private val debateHistoryService: DebateHistoryService,
    @param:Qualifier(SchedulingConfig.DEBATE_CONNECTION_SCHEDULER) private val taskScheduler: TaskScheduler,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateStageChangedEvent,
    ) {
        runCatching {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = event.debateId,
                    type = DebateConnectionEvent.Type.STAGE_CHANGED,
                    stage = event.stage,
                    turnEndsAt = event.turnEndsAt,
                ),
            )
        }.onFailure {
            logger.warn(it) { "토론 단계 전환 WS 이벤트 발행에 실패했습니다. debateId=${event.debateId}" }
        }

        if (isTimedStage(event.stage) && event.turnEndsAt != null) {
            runCatching {
                taskScheduler.schedule(
                    { debateHistoryService.forceAdvanceIfOverdue(debateId = event.debateId) },
                    event.turnEndsAt.toInstant(ZoneOffset.UTC),
                )
            }.onFailure {
                logger.warn(it) { "턴 마감 타이머 등록에 실패했습니다. debateId=${event.debateId}" }
            }
        }
    }

    private fun isTimedStage(
        stage: DebateStage,
    ) = stage == DebateStage.OPENING_PROS || stage == DebateStage.OPENING_CONS
}
