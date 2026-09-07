package com.debate.pangyeori.debate.event

data class DebateQueueChangedEvent(
    val debateId: String,
    val userId: String?,
    val operation: DebateQueueOperation,
) {
    enum class DebateQueueOperation {
        ADD,
        REMOVE,
        CLEAR,
    }
}
