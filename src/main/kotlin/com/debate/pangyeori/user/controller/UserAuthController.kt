package com.debate.pangyeori.user.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.RefreshTokenRequest
import com.debate.pangyeori.user.dto.SignInRequest
import com.debate.pangyeori.user.dto.SignInResponse
import com.debate.pangyeori.user.service.UserAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserAuthController(
    private val userAuthService: UserAuthService,
) {

    @PostMapping("/signin")
    fun signIn(
        @RequestBody @Valid request: SignInRequest,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = userAuthService.signIn(
            email = request.email!!,
            password = request.password!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = userAuthService.refresh(
            refreshToken = request.refreshToken!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @PostMapping("/signout")
    fun logout(
        @RequestBody @Valid request: RefreshTokenRequest,
    ): ResponseEntity<Void> {
        userAuthService.logout(
            refreshToken = request.refreshToken!!,
        )

        return ResponseEntity.noContent().build()
    }
}
