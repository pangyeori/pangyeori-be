package com.debate.pangyeori.debate.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DebateFreeMessageSubmitRequest(
    @field:NotBlank(message = "메시지 내용은 필수입니다.")
    @field:Size(max = 500, message = "메시지는 500자 이하여야 합니다.")
    val content: String?,
)
