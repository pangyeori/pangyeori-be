package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import java.time.LocalDateTime

data class DebateStatusResponse(
    val debateStatus: DebateStatus,
    val guestStatus: DebateUserStatus?,
    val requestList: List<ParticipationRequest>?,
) {
    data class ParticipationRequest(
        val userId: String,
        val nickname: String,
        val status: DebateUserStatus,
        val requestedAt: LocalDateTime,
    )
}
