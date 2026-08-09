package com.debate.pangyeori.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignInRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "올바른 이메일 형식이 아닙니다.")
    @field:Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    val email: String?,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
    val password: String?,
)
