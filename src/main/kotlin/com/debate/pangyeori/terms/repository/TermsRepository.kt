package com.debate.pangyeori.terms.repository

import com.debate.pangyeori.terms.domain.Terms
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface TermsRepository : JpaRepository<Terms, String> {
    @Query(
        """
        SELECT t FROM Terms t
        WHERE t.effectiveAt <= :currentTime
        AND t.version = (
            SELECT MAX(t2.version) FROM Terms t2
            WHERE t2.type = t.type
            AND t2.effectiveAt <= :currentTime
        )
        ORDER BY t.id
        """
    )
    fun findAllLatestEffective(
        currentTime: LocalDateTime,
    ): List<Terms>

    @Query(
        """
        SELECT t.id FROM Terms t
        WHERE t.required = true
        AND t.effectiveAt <= :currentTime
        AND t.version = (
            SELECT MAX(t2.version) FROM Terms t2
            WHERE t2.type = t.type
            AND t2.effectiveAt <= :currentTime
        )
        AND NOT EXISTS (
            SELECT 1 FROM UserTermsAgreement a
            WHERE a.user.email = :email
            AND a.terms.id = t.id
            AND a.withdrawnAt IS NULL
        )
        """
    )
    fun findPendingRequiredIds(
        email: String,
        currentTime: LocalDateTime,
    ): List<String>
}
