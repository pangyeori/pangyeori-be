package com.debate.pangyeori.debate.event

enum class DebateQueueOperation {
    ADD,
    REMOVE,
    CLEAR,
}

data class DebateQueueChangedEvent(
    val debateId: String,
    val userId: String?,
    val operation: DebateQueueOperation,
)
