package com.debate.pangyeori.auth.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PasswordResetConfirmRequest(
    @field:NotBlank(message = "재설정 토큰은 필수입니다.")
    val passwordResetToken: String?,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    @field:Pattern(regexp = "^(?=.*[!@#\$%^&*()_+=-]).+\$", message = "비밀번호는 특수문자를 1개 이상 포함해야 합니다.")
    val newPassword: String?,
)
