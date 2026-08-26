package com.debate.pangyeori.user.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class UserRole(
    override val code: String,
) : CodeEnum {
    USER("USER"),
    ADMIN("ADMIN"),
}
