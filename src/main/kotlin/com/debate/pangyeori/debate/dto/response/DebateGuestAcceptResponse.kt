package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebateStatus

data class DebateGuestAcceptResponse(
    val debateId: String,
    val status: DebateStatus,
)
