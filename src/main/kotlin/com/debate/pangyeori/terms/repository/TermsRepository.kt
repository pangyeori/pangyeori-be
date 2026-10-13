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
}
