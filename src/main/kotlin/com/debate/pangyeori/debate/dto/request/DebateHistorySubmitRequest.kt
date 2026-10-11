package com.debate.pangyeori.debate.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DebateHistorySubmitRequest(
    @field:NotBlank(message = "발언 내용은 필수입니다.")
    @field:Size(max = 1000, message = "발언은 1000자 이하여야 합니다.")
    val content: String?,
)
