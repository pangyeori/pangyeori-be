package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import java.time.LocalDateTime

data class DebateVerdictIssuedEvent(
    val debateId: String,
    val stage: DebateStage,
    val status: DebateVerdictStatus,
    val winnerPosition: WinnerPosition?,
    val reasoning: String?,
    val createdAt: LocalDateTime,
)
