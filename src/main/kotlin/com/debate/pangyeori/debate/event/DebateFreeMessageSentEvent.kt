package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import java.time.LocalDateTime

data class DebateFreeMessageSentEvent(
    val debateId: String,
    val messageId: String,
    val role: DebateUserRole,
    val content: String,
    val createdAt: LocalDateTime,
)
