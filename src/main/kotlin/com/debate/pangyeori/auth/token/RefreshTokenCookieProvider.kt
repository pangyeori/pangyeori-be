package com.debate.pangyeori.auth.token

import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

@Component
class RefreshTokenCookieProvider {

    fun issue(
        refreshToken: String,
        maxAgeSeconds: Long,
    ): ResponseCookie = build(
        value = refreshToken,
        maxAgeSeconds = maxAgeSeconds,
    )

    fun clear(): ResponseCookie = build(
        value = "",
        maxAgeSeconds = 0,
    )

    private fun build(
        value: String,
        maxAgeSeconds: Long,
    ): ResponseCookie = ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite(SAME_SITE)
        .path(COOKIE_PATH)
        .maxAge(maxAgeSeconds)
        .build()

    companion object {
        const val COOKIE_NAME = "refreshToken"
        private const val SAME_SITE = "Lax"
        private const val COOKIE_PATH = "/api/v1/auth"
    }
}
