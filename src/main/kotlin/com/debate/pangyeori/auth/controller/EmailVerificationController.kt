package com.debate.pangyeori.auth.controller

import com.debate.pangyeori.auth.dto.request.EmailVerificationConfirmRequest
import com.debate.pangyeori.auth.dto.request.EmailVerificationRequest
import com.debate.pangyeori.auth.service.EmailVerificationService
import com.debate.pangyeori.common.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/email-verifications")
class EmailVerificationController(
    private val emailVerificationService: EmailVerificationService,
) {

    @PostMapping
    fun sendCode(
        @RequestBody @Valid request: EmailVerificationRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        emailVerificationService.sendCode(
            email = request.email!!,
        )

        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @PostMapping("/confirm")
    fun confirmCode(
        @RequestBody @Valid request: EmailVerificationConfirmRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        emailVerificationService.confirmCode(
            email = request.email!!,
            code = request.code!!,
        )

        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
