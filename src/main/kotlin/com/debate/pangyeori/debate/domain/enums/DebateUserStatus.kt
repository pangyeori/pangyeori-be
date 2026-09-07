package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebateUserStatus(
    override val code: String,
) : CodeEnum {
    PENDING("PENDING"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED"),
    ACCEPTED("ACCEPTED"),
}
