package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class WinnerPosition(
    override val code: String,
) : CodeEnum {
    PROS("PROS"),
    CONS("CONS"),
    DRAW("DRAW"),
}
