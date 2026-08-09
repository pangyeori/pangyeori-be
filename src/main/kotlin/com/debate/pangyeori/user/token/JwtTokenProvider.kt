package com.debate.pangyeori.user.token

import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.ExpiredTokenException
import com.debate.pangyeori.user.exception.InvalidTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class JwtTokenProvider(
    private val jwtEncoder: JwtEncoder,
    @param:Value("\${security.jwt.access-token-expiration:15m}")
    private val accessTokenExpiration: Duration,
    @param:Value("\${security.jwt.refresh-token-expiration:14d}")
    private val refreshTokenExpiration: Duration,
    private val clock: Clock,
    private val jwtDecoder: JwtDecoder,
) : TokenProvider {
    override fun issue(user: User): IssuedTokens {
        val now = Instant.now(clock)
        return IssuedTokens(
            accessToken = encode(user, "access", now, accessTokenExpiration),
            refreshToken = encode(user, "refresh", now, refreshTokenExpiration),
            accessTokenExpiresIn = accessTokenExpiration.seconds,
            refreshTokenExpiresIn = refreshTokenExpiration.seconds,
        )
    }

    override fun parseAccessToken(token: String): TokenClaims = parse(token, "access")

    override fun parseRefreshToken(token: String): TokenClaims = parse(token, "refresh")

    private fun parse(token: String, expectedType: String): TokenClaims {
        try {
            val jwt = jwtDecoder.decode(token)
            if (jwt.getClaimAsString("type") != expectedType) {
                throw InvalidTokenException()
            }
            return TokenClaims(
                subject = jwt.subject ?: throw InvalidTokenException(),
                role = jwt.getClaimAsString("role") ?: throw InvalidTokenException(),
                tokenId = jwt.id ?: throw InvalidTokenException(),
            )
        } catch (e: JwtValidationException) {
            if (e.errors.any { it.description?.contains("expired", ignoreCase = true) == true }) {
                throw ExpiredTokenException()
            }
            throw InvalidTokenException()
        } catch (e: JwtException) {
            throw InvalidTokenException()
        }
    }

    private fun encode(user: User, tokenType: String, issuedAt: Instant, expiration: Duration): String {
        val claims = JwtClaimsSet.builder()
            .issuer("pangyeori")
            .subject(user.email)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(expiration))
            .id(UUID.randomUUID().toString())
            .claim("type", tokenType)
            .claim("role", user.role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
