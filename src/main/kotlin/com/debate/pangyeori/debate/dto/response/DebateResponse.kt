package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import java.time.LocalDateTime

data class DebateResponse(
    val id: String,
    val title: String,
    val description: String?,
    val hostPosition: DebatePosition,
    val guestPosition: DebatePosition,
    val status: DebateStatus,
    val currentStage: DebateStage,
    val turnEndsAt: LocalDateTime?,
    val turnTimeSeconds: Int,
    val freeDebateTimeSeconds: Int,
    val inviteToken: String?,
) {
    companion object {
        fun from(
            debate: Debate,
            isHost: Boolean,
        ) = DebateResponse(
            id = debate.id!!,
            title = debate.title,
            description = debate.description,
            hostPosition = debate.hostPosition,
            guestPosition = debate.hostPosition.opposite(),
            status = debate.status,
            currentStage = debate.currentStage,
            turnEndsAt = debate.currentTurnEndsAt(),
            turnTimeSeconds = debate.turnTimeSeconds,
            freeDebateTimeSeconds = debate.freeDebateTimeSeconds,
            inviteToken = if (isHost) debate.inviteToken else null,
        )
    }
}
