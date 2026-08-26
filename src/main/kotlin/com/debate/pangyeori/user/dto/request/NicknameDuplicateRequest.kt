package com.debate.pangyeori.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class NicknameDuplicateRequest(
    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    @field:Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 특수문자를 포함할 수 없습니다.")
    val nickname: String?,
)
