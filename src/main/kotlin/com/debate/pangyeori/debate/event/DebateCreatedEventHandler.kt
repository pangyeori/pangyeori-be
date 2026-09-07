package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.repository.DebateInviteRedisRepository
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.repository.DebateStatusRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateCreatedEventHandler(
    private val debateInviteRedisRepository: DebateInviteRedisRepository,
    private val debateStatusRedisRepository: DebateStatusRedisRepository,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(
        event: DebateCreatedEvent,
    ) {
        runCatching {
            debateInviteRedisRepository.save(
                token = event.inviteToken,
                debateId = event.debateId,
            )
            debateStatusRedisRepository.save(
                debateId = event.debateId,
                status = DebateStatus.WAITING,
            )
        }.onFailure {
            logger.warn(it) { "토론방 생성 캐시 저장에 실패했습니다. debateId=${event.debateId}" }
        }
    }
}
