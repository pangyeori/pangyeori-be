package com.debate.pangyeori.terms.dto.response

import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.enums.TermsType
import java.time.LocalDateTime

data class TermsListResponse(
    val termsId: String,
    val type: TermsType,
    val version: Int,
    val title: String,
    val content: String,
    val required: Boolean,
    val effectiveAt: LocalDateTime,
) {
    companion object {
        fun from(
            terms: Terms,
        ) = TermsListResponse(
            termsId = terms.id!!,
            type = terms.type,
            version = terms.version,
            title = terms.title,
            content = terms.content,
            required = terms.required,
            effectiveAt = terms.effectiveAt,
        )
    }
}
