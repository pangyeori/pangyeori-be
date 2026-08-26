package com.debate.pangyeori.user.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class UserStatus(
    override val code: String,
) : CodeEnum {
    ACTIVE("ACTIVE"),
    SUSPENDED("SUSPENDED"),
    WITHDRAWN("WITHDRAWN"),
}
