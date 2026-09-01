package com.debate.pangyeori.storage.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ViewUrlRequest(
    @field:NotBlank(message = "오브젝트 키는 필수입니다.")
    @field:Pattern(regexp = "^[a-z0-9-]+(/[A-Za-z0-9-]+)+\\.[a-z0-9]+$", message = "올바른 오브젝트 키 형식이 아닙니다.")
    val objectKey: String?,
)
