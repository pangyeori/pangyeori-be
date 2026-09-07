package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.repository.DebateQueueRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateQueueChangedEventHandler(
    private val debateQueueRedisRepository: DebateQueueRedisRepository,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateQueueChangedEvent,
    ) {
        runCatching {
            when (event.operation) {
                DebateQueueOperation.ADD -> debateQueueRedisRepository.add(
                    debateId = event.debateId,
                    userId = event.userId!!,
                )
                DebateQueueOperation.REMOVE -> debateQueueRedisRepository.remove(
                    debateId = event.debateId,
                    userId = event.userId!!,
                )
                DebateQueueOperation.CLEAR -> debateQueueRedisRepository.clear(
                    debateId = event.debateId,
                )
            }
        }.onFailure {
            logger.warn(it) { "토론방 참여 대기열 캐시 갱신에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
