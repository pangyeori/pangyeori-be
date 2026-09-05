package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateStatus

data class DebateStatusChangedEvent(
    val debateId: String,
    val status: DebateStatus,
)
