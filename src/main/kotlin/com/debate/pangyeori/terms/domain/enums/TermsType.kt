package com.debate.pangyeori.terms.domain.enums

import com.debate.pangyeori.common.converter.CodeEnum

enum class TermsType(
    override val code: String,
) : CodeEnum {
    TERMS_OF_SERVICE("TERMS_OF_SERVICE"),
    PRIVACY_POLICY("PRIVACY_POLICY"),
    MARKETING("MARKETING"),
}
