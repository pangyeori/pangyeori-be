package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import java.time.LocalDateTime

data class DebateInvitationResponse(
    val debateId: String,
    val title: String,
    val description: String?,
    val hostNickname: String,
    val guestPosition: DebatePosition,
    val debateStatus: DebateStatus,
    val guestStatus: DebateUserStatus?,
    val turnTimeSeconds: Int,
    val freeDebateTimeSeconds: Int,
    val createdAt: LocalDateTime,
)
