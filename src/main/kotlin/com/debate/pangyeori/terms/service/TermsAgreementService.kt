package com.debate.pangyeori.terms.service

import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.UserTermsAgreement
import com.debate.pangyeori.terms.domain.enums.TermsType
import com.debate.pangyeori.terms.dto.response.TermsListResponse
import com.debate.pangyeori.terms.exception.RequiredTermsNotAgreedException
import com.debate.pangyeori.terms.exception.RequiredTermsWithdrawalNotAllowedException
import com.debate.pangyeori.terms.exception.TermsNotFoundException
import com.debate.pangyeori.terms.exception.TermsNotLatestException
import com.debate.pangyeori.terms.repository.TermsRepository
import com.debate.pangyeori.terms.repository.UserTermsAgreementRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class TermsAgreementService(
    private val termsRepository: TermsRepository,
    private val userTermsAgreementRepository: UserTermsAgreementRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun agreeAtSignUp(
        user: User,
        agreedTermsIds: List<String>,
    ) {
        val distinctIds = agreedTermsIds.distinct()
        val agreedTerms = termsRepository.findAllById(distinctIds)
        if (agreedTerms.size != distinctIds.size) {
            throw TermsNotFoundException()
        }

        val latestTerms = termsRepository.findAllLatestEffective(
            currentTime = LocalDateTime.now(ZoneOffset.UTC),
        )
        val latestIds = latestTerms.map { it.id!! }.toSet()
        if (agreedTerms.any { it.id !in latestIds }) {
            throw TermsNotLatestException()
        }
        if (latestTerms.any { it.required && it.id !in distinctIds }) {
            throw RequiredTermsNotAgreedException()
        }

        userTermsAgreementRepository.saveAll(
            agreedTerms.map {
                UserTermsAgreement.create(
                    user = user,
                    terms = it,
                )
            },
        )
    }

    @Transactional
    fun changeAgreement(
        email: String,
        termsId: String,
        agreed: Boolean,
    ) {
        val user = userRepository.findByEmail(
            email = email,
        ) ?: throw UserNotFoundException()
        val terms = termsRepository.findById(termsId).orElseThrow { TermsNotFoundException() }
        val latestIds = termsRepository.findAllLatestEffective(
            currentTime = LocalDateTime.now(ZoneOffset.UTC),
        ).map { it.id!! }
        if (terms.id !in latestIds) {
            throw TermsNotLatestException()
        }

        if (agreed) {
            agree(
                user = user,
                terms = terms,
            )
            return
        }

        if (terms.required) {
            throw RequiredTermsWithdrawalNotAllowedException()
        }
        userTermsAgreementRepository.findAllByUserIdAndTermsTypeAndWithdrawnAtIsNull(
            userId = user.id!!,
            termsType = terms.type,
        ).forEach { it.withdraw() }
    }

    @Transactional
    fun agreeTerms(
        email: String,
        termsIds: List<String>,
    ) {
        val user = userRepository.findByEmail(
            email = email,
        ) ?: throw UserNotFoundException()
        val distinctIds = termsIds.distinct()
        val targetTerms = termsRepository.findAllById(distinctIds)
        if (targetTerms.size != distinctIds.size) {
            throw TermsNotFoundException()
        }
        val latestIds = termsRepository.findAllLatestEffective(
            currentTime = LocalDateTime.now(ZoneOffset.UTC),
        ).map { it.id!! }.toSet()
        if (targetTerms.any { it.id !in latestIds }) {
            throw TermsNotLatestException()
        }

        targetTerms.forEach {
            agree(
                user = user,
                terms = it,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getPendingTerms(
        email: String,
    ): List<TermsListResponse> {
        val user = userRepository.findByEmail(
            email = email,
        ) ?: throw UserNotFoundException()

        return termsRepository.findAllById(
            termsRepository.findPendingRequiredIds(
                email = user.email,
                currentTime = LocalDateTime.now(ZoneOffset.UTC),
            ),
        ).sortedBy {
            it.type.ordinal
        }.map {
            TermsListResponse.from(
                terms = it,
            )
        }
    }

    @Transactional(readOnly = true)
    fun isMarketingAgreed(
        userId: String,
    ) = userTermsAgreementRepository.existsByUserIdAndTermsTypeAndWithdrawnAtIsNull(
        userId = userId,
        termsType = TermsType.MARKETING,
    )

    private fun agree(
        user: User,
        terms: Terms,
    ) {
        val agreement = userTermsAgreementRepository.findByUserIdAndTermsId(
            userId = user.id!!,
            termsId = terms.id!!,
        )
        if (agreement == null) {
            userTermsAgreementRepository.save(
                UserTermsAgreement.create(
                    user = user,
                    terms = terms,
                ),
            )
        } else {
            agreement.agreeAgain()
        }
    }
}
