package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebatePosition

data class DebateParticipationResponse(
    val debateId: String,
    val position: DebatePosition,
)
