package com.debate.pangyeori.terms.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.terms.dto.response.TermsListResponse
import com.debate.pangyeori.terms.service.TermsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/terms")
class TermsController(
    private val termsService: TermsService,
) {
    @GetMapping
    fun getLatestTerms(): ResponseEntity<ApiResponse<List<TermsListResponse>>> {
        val response = termsService.getLatestTerms()

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }
}
