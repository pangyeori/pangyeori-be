package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.repository.DebateStatusRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateStatusChangedEventHandler(
    private val debateStatusRedisRepository: DebateStatusRedisRepository,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handle(
        event: DebateStatusChangedEvent,
    ) {
        runCatching {
            debateStatusRedisRepository.save(
                debateId = event.debateId,
                status = event.status,
            )
        }.onFailure {
            logger.warn(it) { "토론방 상태 캐시 갱신에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
