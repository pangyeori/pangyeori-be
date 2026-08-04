package com.debate.pangyeori.user.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.EmailVerificationConfirmRequest
import com.debate.pangyeori.user.dto.EmailVerificationRequest
import com.debate.pangyeori.user.service.EmailVerificationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/{version}/email-verifications")
class EmailVerificationController(
    private val emailVerificationService: EmailVerificationService,
) {

    @PostMapping(version = "1")
    fun sendCode(
        @RequestBody @Valid request: EmailVerificationRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        emailVerificationService.sendCode(
            email = request.email!!,
        )

        return ResponseEntity.ok(ApiResponse.success(Unit))
    }

    @PostMapping("/confirm", version = "1")
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
