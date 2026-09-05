package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.Debate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType

interface DebateRepository : JpaRepository<Debate, String> {
    fun findByInviteToken(
        inviteToken: String,
    ): Debate?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(
        id: String,
    ): Debate?
}
