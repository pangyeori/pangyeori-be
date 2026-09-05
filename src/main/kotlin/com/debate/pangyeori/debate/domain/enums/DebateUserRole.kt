package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebateUserRole(
    override val code: String,
) : CodeEnum {
    HOST("HOST"),
    GUEST("GUEST"),
}
