package com.debate.pangyeori.debate.dto.response

import com.debate.pangyeori.debate.domain.enums.DebateUserRole

data class DebateEarlyEndResponse(
    val pending: Boolean,
    val proposerRole: DebateUserRole?,
)
