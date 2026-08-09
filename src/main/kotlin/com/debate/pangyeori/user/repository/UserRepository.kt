package com.debate.pangyeori.user.repository

import com.debate.pangyeori.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, String> {
    fun findByEmail(
        email: String,
    ): User?

    fun existsByEmail(
        email: String,
    ): Boolean

    fun existsByNickname(
        nickname: String,
    ): Boolean
}
