package com.debate.pangyeori.user.dto.request

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UserProfileUpdateRequest(
    @field:Size(min = 2, max = 12, message = "닉네임은 2자 이상 12자 이하여야 합니다.")
    @field:Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 특수문자를 포함할 수 없습니다.")
    val nickname: String?,

    @field:Pattern(regexp = "^profile-images/[A-Za-z0-9-]+(/[A-Za-z0-9-]+)*\\.[a-z0-9]+$", message = "올바른 이미지 키 형식이 아닙니다.")
    val profileImageKey: String?,
)
