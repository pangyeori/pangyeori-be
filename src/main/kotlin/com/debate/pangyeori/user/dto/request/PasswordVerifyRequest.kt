package com.debate.pangyeori.user.dto.request

import jakarta.validation.constraints.NotBlank

data class PasswordVerifyRequest(
    @field:NotBlank(message = "현재 비밀번호는 필수입니다.")
    val currentPassword: String?,
)
