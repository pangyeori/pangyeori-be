package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import org.springframework.data.jpa.repository.JpaRepository

interface DebateVerdictRepository : JpaRepository<DebateVerdict, String> {
    fun findByDebateIdAndStage(
        debateId: String,
        stage: DebateStage,
    ): DebateVerdict?

    fun findAllByDebateIdOrderByCreatedAtAsc(
        debateId: String,
    ): List<DebateVerdict>
}
