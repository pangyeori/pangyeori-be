package com.debate.pangyeori.debate.dto.request

import jakarta.validation.constraints.NotBlank

data class DebateGuestAcceptRequest(
    @field:NotBlank(message = "사용자 ID는 필수입니다.")
    val userId: String?,
)
