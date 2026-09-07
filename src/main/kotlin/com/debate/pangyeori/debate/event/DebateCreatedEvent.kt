package com.debate.pangyeori.debate.event

data class DebateCreatedEvent(
    val debateId: String,
    val inviteToken: String,
)
