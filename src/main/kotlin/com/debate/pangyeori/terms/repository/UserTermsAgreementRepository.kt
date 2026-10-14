package com.debate.pangyeori.terms.repository

import com.debate.pangyeori.terms.domain.UserTermsAgreement
import com.debate.pangyeori.terms.domain.enums.TermsType
import org.springframework.data.jpa.repository.JpaRepository

interface UserTermsAgreementRepository : JpaRepository<UserTermsAgreement, String> {
    fun findByUserIdAndTermsId(
        userId: String,
        termsId: String,
    ): UserTermsAgreement?

    fun findAllByUserIdAndTermsTypeAndWithdrawnAtIsNull(
        userId: String,
        termsType: TermsType,
    ): List<UserTermsAgreement>

    fun existsByUserIdAndTermsIdAndWithdrawnAtIsNull(
        userId: String,
        termsId: String,
    ): Boolean

    fun existsByUserIdAndTermsTypeAndWithdrawnAtIsNull(
        userId: String,
        termsType: TermsType,
    ): Boolean
}
