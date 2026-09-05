package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebatePosition(
    override val code: String,
) : CodeEnum {
    PROS("PROS"),
    CONS("CONS"),
    ;

    fun opposite() = when (this) {
        PROS -> CONS
        CONS -> PROS
    }
}
