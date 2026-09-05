package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.repository.DebateInviteRedisRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DebateCreatedEventHandler(
    private val debateInviteRedisRepository: DebateInviteRedisRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handle(
        event: DebateCreatedEvent,
    ) {
        debateInviteRedisRepository.save(
            token = event.inviteToken,
            debateId = event.debateId,
        )
    }
}
