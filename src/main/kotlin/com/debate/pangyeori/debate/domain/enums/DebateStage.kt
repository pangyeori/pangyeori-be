package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebateStage(
    override val code: String,
) : CodeEnum {
    NONE("NONE"),
    OPENING_PROS("OPENING_PROS"),
    OPENING_CONS("OPENING_CONS"),
    VERDICT_1("VERDICT_1"),
    REBUTTAL_PROS("REBUTTAL_PROS"),
    REBUTTAL_CONS("REBUTTAL_CONS"),
    VERDICT_2("VERDICT_2"),
    FREE("FREE"),
    FINAL_VERDICT("FINAL_VERDICT"),
}
