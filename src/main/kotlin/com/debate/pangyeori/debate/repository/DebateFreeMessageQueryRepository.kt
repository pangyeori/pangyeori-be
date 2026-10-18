package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateFreeMessage

interface DebateFreeMessageQueryRepository {
    fun findAllByDebateId(
        debateId: String,
        cursor: String?,
        limit: Int,
    ): List<DebateFreeMessage>
}
