package com.debate.pangyeori.debate.websocket.message

import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import java.time.LocalDateTime

data class DebateConnectionEvent(
    val debateId: String,
    val type: Type,
    val role: DebateUserRole? = null,
    val stage: DebateStage? = null,
    val content: String? = null,
    val turnEndsAt: LocalDateTime? = null,
) {
    enum class Type {
        ENTERED,
        LEFT,
        STARTED,
        STAGE_CHANGED,
        STATEMENT_SUBMITTED,
        STATEMENT_TIMED_OUT,
    }
}
