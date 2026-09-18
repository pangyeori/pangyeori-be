package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.user.domain.User
import java.time.LocalDateTime

data class DebateListResponse(
    val debateId: String,
    val title: String,
    val description: String?,
    val debateStatus: DebateStatus,
    val currentStage: DebateStage,
    val myRole: DebateUserRole,
    val myPosition: DebatePosition,
    val opponent: Opponent?,
    val turnTimeSeconds: Int,
    val freeDebateTimeSeconds: Int,
    val createdAt: LocalDateTime,
) {
    data class Opponent(
        val userId: String,
        val nickname: String,
        val profileImageKey: String?,
    ) {
        companion object {
            fun from(
                user: User,
            ) = Opponent(
                userId = user.id!!,
                nickname = user.nickname,
                profileImageKey = user.profileImageKey,
            )
        }
    }

    companion object {
        fun from(
            member: DebateUser,
        ): DebateListResponse {
            val debate = member.debate
            val opponent = if (member.role == DebateUserRole.HOST) debate.guest else debate.host

            return DebateListResponse(
                debateId = debate.id!!,
                title = debate.title,
                description = debate.description,
                debateStatus = debate.status,
                currentStage = debate.currentStage,
                myRole = member.role,
                myPosition = member.position,
                opponent = opponent?.let {
                    Opponent.from(
                        user = it,
                    )
                },
                turnTimeSeconds = debate.turnTimeSeconds,
                freeDebateTimeSeconds = debate.freeDebateTimeSeconds,
                createdAt = debate.createdAt!!,
            )
        }
    }
}
