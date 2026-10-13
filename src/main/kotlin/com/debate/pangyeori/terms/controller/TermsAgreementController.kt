package com.debate.pangyeori.terms.controller

import com.debate.pangyeori.terms.dto.request.TermsAgreementChangeRequest
import com.debate.pangyeori.terms.service.TermsAgreementService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/users/me/term-agreements")
class TermsAgreementController(
    private val termsAgreementService: TermsAgreementService,
) {
    @PutMapping("/{termsId}")
    fun changeAgreement(
        principal: Principal,
        @PathVariable termsId: String,
        @RequestBody @Valid request: TermsAgreementChangeRequest,
    ): ResponseEntity<Void> {
        termsAgreementService.changeAgreement(
            email = principal.name,
            termsId = termsId,
            agreed = request.agreed!!,
        )

        return ResponseEntity.noContent().build()
    }
}
