package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebateStage
import java.time.LocalDateTime

data class DebateHistoryResponse(
    val id: String,
    val stage: DebateStage,
    val content: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            history: DebateHistory,
        ) = DebateHistoryResponse(
            id = history.id!!,
            stage = history.stage,
            content = history.content,
            createdAt = history.createdAt!!,
        )
    }
}
