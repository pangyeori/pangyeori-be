package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus

data class DebateCreateResponse(
    val id: String,
    val title: String,
    val description: String?,
    val hostPosition: DebatePosition,
    val guestPosition: DebatePosition,
    val status: DebateStatus,
    val turnTimeSeconds: Int,
    val freeDebateTimeSeconds: Int,
    val inviteToken: String,
    val members: List<DebateMemberResponse>,
) {
    companion object {
        fun from(
            debate: Debate,
            hostMember: DebateUser,
        ) = DebateCreateResponse(
            id = debate.id!!,
            title = debate.title,
            description = debate.description,
            hostPosition = debate.hostPosition,
            guestPosition = debate.hostPosition.opposite(),
            status = debate.status,
            turnTimeSeconds = debate.turnTimeSeconds,
            freeDebateTimeSeconds = debate.freeDebateTimeSeconds,
            inviteToken = debate.inviteToken,
            members = listOf(
                DebateMemberResponse.from(
                    debateUser = hostMember,
                ),
            ),
        )
    }
}
