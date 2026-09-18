package com.debate.pangyeori.debate.dto.request

import com.debate.pangyeori.common.validation.EnumValue
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole

data class DebateListRequest(
    @field:EnumValue(enumClass = DebateStatus::class, message = "유효하지 않은 토론방 상태입니다.")
    val status: String?,

    @field:EnumValue(enumClass = DebateUserRole::class, message = "유효하지 않은 역할입니다.")
    val role: String?,

    val keyword: String?,

    val cursor: String?,

    val pageSize: Int?,
)
