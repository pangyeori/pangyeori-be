package com.debate.pangyeori.auth.token

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RefreshTokenCookieProvider(
    @param:Value("\${security.jwt.refresh-token-expiration:14d}")
    private val refreshTokenExpiration: Duration,
) {

    fun issue(
        refreshToken: String,
    ): ResponseCookie = build(
        value = refreshToken,
        maxAgeSeconds = refreshTokenExpiration.seconds,
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
