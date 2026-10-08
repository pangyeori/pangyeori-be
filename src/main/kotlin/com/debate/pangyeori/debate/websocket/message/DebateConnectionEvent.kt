package com.debate.pangyeori.debate.websocket.message

import com.debate.pangyeori.debate.domain.enums.DebateUserRole

data class DebateConnectionEvent(
    val debateId: String,
    val type: Type,
    val role: DebateUserRole? = null,
) {
    enum class Type {
        ENTERED,
        LEFT,
        STARTED,
    }
}
