package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateUserStatus

data class DebateGuestStatusChangedEvent(
    val debateId: String,
    val userId: String,
    val status: DebateUserStatus,
)
