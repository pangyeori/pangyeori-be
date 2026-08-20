package com.debate.pangyeori.auth.token

import com.debate.pangyeori.user.domain.User

interface TokenProvider {
    fun issue(
        user: User,
    ): IssuedTokens

    fun parseAccessToken(
        token: String,
    ): TokenClaims

    fun parseRefreshToken(
        token: String,
    ): TokenClaims

    data class IssuedTokens(
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresIn: Long,
        val refreshTokenExpiresIn: Long,
    )

    data class TokenClaims(
        val subject: String,
        val role: String,
        val tokenId: String,
    )
}
