package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import java.time.LocalDateTime

data class DebateVerdictResponse(
    val id: String,
    val stage: DebateStage,
    val status: DebateVerdictStatus,
    val winnerPosition: WinnerPosition?,
    val reasoning: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            verdict: DebateVerdict,
        ) = DebateVerdictResponse(
            id = verdict.id!!,
            stage = verdict.stage,
            status = verdict.status,
            winnerPosition = verdict.winnerPosition,
            reasoning = verdict.reasoning,
            createdAt = verdict.createdAt!!,
        )
    }
}
