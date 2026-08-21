package com.debate.pangyeori.auth.controller

import com.debate.pangyeori.auth.dto.request.PasswordResetConfirmRequest
import com.debate.pangyeori.auth.dto.request.PasswordResetRequest
import com.debate.pangyeori.auth.dto.response.PasswordResetResponse
import com.debate.pangyeori.auth.service.PasswordResetService
import com.debate.pangyeori.common.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/password-resets")
class PasswordResetController(
    private val passwordResetService: PasswordResetService,
) {

    @PostMapping
    fun issueToken(
        @RequestBody @Valid request: PasswordResetRequest,
    ): ResponseEntity<ApiResponse<PasswordResetResponse>> {
        val response = passwordResetService.issueToken(
            email = request.email!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @PostMapping("/confirm")
    fun resetPassword(
        @RequestBody @Valid request: PasswordResetConfirmRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.resetPassword(
            passwordResetToken = request.passwordResetToken!!,
            newPassword = request.newPassword!!,
        )

        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
