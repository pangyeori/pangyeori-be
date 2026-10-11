package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateStage
import java.time.LocalDateTime

data class DebateStageChangedEvent(
    val debateId: String,
    val stage: DebateStage,
    val turnEndsAt: LocalDateTime?,
)
