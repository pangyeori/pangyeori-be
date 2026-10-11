package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import java.time.LocalDateTime

data class DebateHistoryRecordedEvent(
    val debateId: String,
    val stage: DebateStage,
    val role: DebateUserRole,
    val content: String?,
    val createdAt: LocalDateTime,
)
