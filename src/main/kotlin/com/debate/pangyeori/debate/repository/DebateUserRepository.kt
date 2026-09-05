package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import org.springframework.data.jpa.repository.JpaRepository

interface DebateUserRepository : JpaRepository<DebateUser, String> {
    fun findByDebateIdAndUserId(
        debateId: String,
        userId: String,
    ): DebateUser?

    fun findAllByDebateIdAndStatusOrderByCreatedAtAsc(
        debateId: String,
        status: DebateUserStatus,
    ): List<DebateUser>

    fun findAllByDebateIdAndStatus(
        debateId: String,
        status: DebateUserStatus,
    ): List<DebateUser>
}
