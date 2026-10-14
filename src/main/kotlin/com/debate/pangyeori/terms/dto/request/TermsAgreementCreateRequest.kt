package com.debate.pangyeori.terms.dto.request

import jakarta.validation.constraints.NotEmpty

data class TermsAgreementCreateRequest(
    @field:NotEmpty(message = "동의할 약관 목록은 필수입니다.")
    val termsIds: List<String>?,
)
