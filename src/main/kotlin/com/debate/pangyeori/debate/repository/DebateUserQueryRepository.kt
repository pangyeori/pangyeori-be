package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole

interface DebateUserQueryRepository {
    fun findAllParticipating(
        userId: String,
        status: DebateStatus?,
        role: DebateUserRole?,
        keyword: String?,
        cursor: String?,
        limit: Int,
    ): List<DebateUser>
}
