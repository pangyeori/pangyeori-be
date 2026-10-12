package com.debate.pangyeori.debate.websocket.message

import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import java.time.LocalDateTime

data class DebateConnectionEvent(
    val debateId: String,
    val type: Type,
    val role: DebateUserRole? = null,
    val stage: DebateStage? = null,
    val content: String? = null,
    val turnEndsAt: LocalDateTime? = null,
    val submittedAt: LocalDateTime? = null,
    val verdictStatus: DebateVerdictStatus? = null,
    val winnerPosition: WinnerPosition? = null,
    val reasoning: String? = null,
) {
    enum class Type {
        ENTERED,
        LEFT,
        STARTED,
        STAGE_CHANGED,
        STATEMENT_SUBMITTED,
        STATEMENT_TIMED_OUT,
        VERDICT_ISSUED,
    }
}
