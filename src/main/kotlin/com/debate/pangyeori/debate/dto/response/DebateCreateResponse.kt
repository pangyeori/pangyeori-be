package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus

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

data class DebateMemberResponse(
    val userId: String,
    val nickname: String,
    val role: DebateUserRole,
    val position: DebatePosition,
    val status: DebateUserStatus,
) {
    companion object {
        fun from(
            debateUser: DebateUser,
        ) = DebateMemberResponse(
            userId = debateUser.user.id!!,
            nickname = debateUser.user.nickname,
            role = debateUser.role,
            position = debateUser.position,
            status = debateUser.status,
        )
    }
}
