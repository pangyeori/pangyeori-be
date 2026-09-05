package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus

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
