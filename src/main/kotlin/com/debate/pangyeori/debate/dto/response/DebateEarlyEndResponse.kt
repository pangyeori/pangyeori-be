package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import java.time.LocalDateTime

data class DebateEarlyEndResponse(
    val pending: Boolean,
    val proposerRole: DebateUserRole?,
    val expiresAt: LocalDateTime?,
)
