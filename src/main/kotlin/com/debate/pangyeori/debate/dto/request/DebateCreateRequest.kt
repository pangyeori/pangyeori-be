package com.debate.pangyeori.debate.dto.request

import com.debate.pangyeori.common.validation.EnumValue
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range

data class DebateCreateRequest(
    @field:NotBlank(message = "토론 주제는 필수입니다.")
    @field:Size(max = 100, message = "토론 주제는 100자 이하여야 합니다.")
    val title: String?,

    val description: String?,

    @field:NotBlank(message = "개설자 포지션은 필수입니다.")
    @field:EnumValue(enumClass = DebatePosition::class, message = "유효하지 않은 포지션입니다.")
    val hostPosition: String?,

    @field:NotNull(message = "턴 시간은 필수입니다.")
    @field:Range(min = 30, max = 600, message = "턴 시간은 {min}초 이상 {max}초 이하여야 합니다.")
    val turnTimeSeconds: Int?,

    @field:NotNull(message = "자유 토론 시간은 필수입니다.")
    @field:Range(min = 60, max = 1800, message = "자유 토론 시간은 {min}초 이상 {max}초 이하여야 합니다.")
    val freeDebateTimeSeconds: Int?,
)
