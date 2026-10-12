package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebateVerdictStatus(
    override val code: String,
) : CodeEnum {
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
}
