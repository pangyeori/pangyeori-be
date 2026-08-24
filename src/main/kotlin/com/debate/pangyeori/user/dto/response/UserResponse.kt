package com.debate.pangyeori.user.dto.response

import com.debate.pangyeori.user.domain.User
import java.time.LocalDateTime

data class UserResponse(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val joinedAt: LocalDateTime,
) {
    companion object {
        fun from(
            user: User,
        ) = UserResponse(
            id = user.id!!,
            email = user.email,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            joinedAt = user.createdAt!!,
        )
    }
}
