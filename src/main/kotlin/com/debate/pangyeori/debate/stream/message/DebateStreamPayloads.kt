package com.debate.pangyeori.debate.stream.message

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.dto.response.DebateStatusResponse

data class DebateStatusChangedPayload(
    val debateStatus: DebateStatus,
)

data class QueueChangedPayload(
    val requestList: List<DebateStatusResponse.ParticipationRequest>,
)

data class GuestStatusChangedPayload(
    val guestStatus: DebateUserStatus,
)
