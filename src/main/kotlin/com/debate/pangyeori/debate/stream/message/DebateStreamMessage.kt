package com.debate.pangyeori.debate.stream.message

import com.debate.pangyeori.debate.domain.enums.DebateUserRole

data class DebateStreamMessage(
    val debateId: String,
    val target: Target,
    val eventName: String?,
    val data: Any?,
    val close: Boolean = false,
) {
    data class Target(
        val type: Type,
        val userId: String?,
    ) {
        fun matches(
            subscriberUserId: String,
            subscriberRole: DebateUserRole,
        ): Boolean = when (type) {
            Type.ALL -> true
            Type.HOST -> subscriberRole == DebateUserRole.HOST
            Type.USER -> subscriberUserId == userId
        }

        enum class Type {
            ALL,
            HOST,
            USER,
        }

        companion object {
            fun all() = Target(
                type = Type.ALL,
                userId = null,
            )

            fun host() = Target(
                type = Type.HOST,
                userId = null,
            )

            fun user(
                userId: String,
            ) = Target(
                type = Type.USER,
                userId = userId,
            )
        }
    }
}
