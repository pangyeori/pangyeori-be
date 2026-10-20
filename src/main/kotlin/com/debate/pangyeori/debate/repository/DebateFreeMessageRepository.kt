package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateFreeMessage
import org.springframework.data.jpa.repository.JpaRepository

interface DebateFreeMessageRepository : JpaRepository<DebateFreeMessage, String>, DebateFreeMessageQueryRepository {
    fun findAllByDebateIdOrderByCreatedAtAscIdAsc(
        debateId: String,
    ): List<DebateFreeMessage>
}
