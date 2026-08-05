package com.debate.pangyeori.user.dto

import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.UserRole
import com.debate.pangyeori.user.domain.UserStatus

data class UserCreateResponse(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val role: UserRole,
    val status: UserStatus,
) {
    companion object {
        fun from(
            user: User,
        ) = UserCreateResponse(
            id = requireNotNull(user.id),
            email = user.email,
            nickname = user.nickname,
            profileImageUrl = user.profileImageUrl,
            role = user.role,
            status = user.status,
        )
    }
}
