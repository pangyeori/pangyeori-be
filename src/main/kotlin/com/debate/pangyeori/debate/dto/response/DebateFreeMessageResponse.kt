package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import java.time.LocalDateTime

data class DebateFreeMessageResponse(
    val id: String,
    val role: DebateUserRole,
    val content: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            message: DebateFreeMessage,
        ) = DebateFreeMessageResponse(
            id = message.id!!,
            role = message.role,
            content = message.content,
            createdAt = message.createdAt!!,
        )
    }
}
