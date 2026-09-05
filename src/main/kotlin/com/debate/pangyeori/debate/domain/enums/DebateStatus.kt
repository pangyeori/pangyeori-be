package com.debate.pangyeori.debate.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class DebateStatus(
    override val code: String,
) : CodeEnum {
    WAITING("WAITING"),
    READY("READY"),
    IN_PROGRESS("IN_PROGRESS"),
    PAUSED("PAUSED"),
    FINISHED("FINISHED"),
    CANCELLED("CANCELLED"),
}
