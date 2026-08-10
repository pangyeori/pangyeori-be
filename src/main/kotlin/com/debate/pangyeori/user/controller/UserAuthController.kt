package com.debate.pangyeori.user.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.SignInRequest
import com.debate.pangyeori.user.dto.SignInResponse
import com.debate.pangyeori.user.service.UserAuthService
import com.debate.pangyeori.user.token.RefreshTokenCookieProvider
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserAuthController(
    private val userAuthService: UserAuthService,
    private val refreshTokenCookieProvider: RefreshTokenCookieProvider,
) {

    @PostMapping("/signin")
    fun signIn(
        @RequestBody @Valid request: SignInRequest,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = userAuthService.signIn(
            email = request.email!!,
            password = request.password!!,
        )

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.issue(
                    refreshToken = response.refreshToken,
                    maxAgeSeconds = response.refreshTokenExpiresIn,
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
        @CookieValue(RefreshTokenCookieProvider.COOKIE_NAME) refreshToken: String,
    ): ResponseEntity<ApiResponse<SignInResponse>> {
        val response = userAuthService.refresh(
            refreshToken = refreshToken,
        )

        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieProvider.issue(
                    refreshToken = response.refreshToken,
                    maxAgeSeconds = response.refreshTokenExpiresIn,
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
        @CookieValue(RefreshTokenCookieProvider.COOKIE_NAME) refreshToken: String,
    ): ResponseEntity<Void> {
        userAuthService.logout(
            refreshToken = refreshToken,
        )

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.clear().toString())
            .build()
    }
}
