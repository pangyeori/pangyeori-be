package com.debate.pangyeori.terms.service

import com.debate.pangyeori.terms.dto.response.TermsListResponse
import com.debate.pangyeori.terms.repository.TermsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class TermsService(
    private val termsRepository: TermsRepository,
) {
    @Transactional(readOnly = true)
    fun getLatestTerms() = termsRepository.findAllLatestEffective(
        currentTime = LocalDateTime.now(ZoneOffset.UTC),
    ).sortedBy {
        it.type.ordinal
    }.map {
        TermsListResponse.from(
            terms = it,
        )
    }
}
