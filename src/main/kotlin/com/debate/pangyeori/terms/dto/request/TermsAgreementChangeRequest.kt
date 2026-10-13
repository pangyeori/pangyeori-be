package com.debate.pangyeori.terms.dto.request

import jakarta.validation.constraints.NotNull

data class TermsAgreementChangeRequest(
    @field:NotNull(message = "동의 여부는 필수입니다.")
    val agreed: Boolean?,
)
