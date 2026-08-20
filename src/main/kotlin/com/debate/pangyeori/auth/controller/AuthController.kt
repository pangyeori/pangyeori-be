package com.debate.pangyeori.auth.controller

import com.debate.pangyeori.auth.dto.request.SignInRequest
import com.debate.pangyeori.auth.dto.response.SignInResponse
import com.debate.pangyeori.auth.exception.InvalidTokenException
import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.auth.token.RefreshTokenCookieProvider
import com.debate.pangyeori.common.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshTokenCookieProvider: RefreshTokenCookieProvider,
) {

    @PostMapping("/signin")
    fun signIn(
        @RequestBody @Valid request: SignInRequest,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = authService.signIn(
            email = request.email!!,
            password = request.password!!,
        )

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.issue(
                    refreshToken = response.refreshToken,
                ).toString(),
            )
            .body(
                ApiResponse.success(
                    data = response,
                ),
            )
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(
            name = RefreshTokenCookieProvider.COOKIE_NAME,
            required = false,
        )
        refreshToken: String?,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = authService.refresh(
            refreshToken = refreshToken ?: throw InvalidTokenException(),
        )

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.issue(
                    refreshToken = response.refreshToken,
                ).toString(),
            )
            .body(
                ApiResponse.success(
                    data = response,
                ),
            )
    }

    @PostMapping("/signout")
    fun logout(
        @CookieValue(
            name = RefreshTokenCookieProvider.COOKIE_NAME,
            required = false,
        )
        refreshToken: String?,
    ): ResponseEntity<Void> {
        if (refreshToken != null) {
            authService.logout(
                refreshToken = refreshToken,
            )
        }

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.clear().toString())
            .build()
    }
}
