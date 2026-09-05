package com.debate.pangyeori.debate.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class DebateCreateRequest(
    @field:NotBlank(message = "토론 주제는 필수입니다.")
    @field:Size(max = 100, message = "토론 주제는 100자 이하여야 합니다.")
    val title: String?,

    val description: String?,

    val hostPosition: String?,

    @field:NotNull(message = "턴 시간은 필수입니다.")
    val turnTimeSeconds: Int?,

    @field:NotNull(message = "자유 토론 시간은 필수입니다.")
    val freeDebateTimeSeconds: Int?,
)
