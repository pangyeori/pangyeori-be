package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebateStage
import org.springframework.data.jpa.repository.JpaRepository

interface DebateHistoryRepository : JpaRepository<DebateHistory, String> {
    fun findByDebateIdAndStage(
        debateId: String,
        stage: DebateStage,
    ): DebateHistory?

    fun findAllByDebateIdOrderByCreatedAtAsc(
        debateId: String,
    ): List<DebateHistory>
}
