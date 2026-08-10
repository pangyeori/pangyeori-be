package com.debate.pangyeori.user.token

import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.ExpiredTokenException
import com.debate.pangyeori.user.exception.InvalidTokenException
import io.hypersistence.tsid.TSID
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class JwtTokenProvider(
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: JwtDecoder,
    @param:Value("\${security.jwt.issuer}")
    private val issuer: String,
    @param:Value("\${security.jwt.access-token-expiration:15m}")
    private val accessTokenExpiration: Duration,
    @param:Value("\${security.jwt.refresh-token-expiration:14d}")
    private val refreshTokenExpiration: Duration,
) : TokenProvider {

    override fun issue(
        user: User,
    ): TokenProvider.IssuedTokens {
        val issuedAt = Instant.now()

        return TokenProvider.IssuedTokens(
            accessToken = encode(
                user = user,
                tokenType = ACCESS_TOKEN_TYPE,
                issuedAt = issuedAt,
                expiration = accessTokenExpiration,
            ),
            refreshToken = encode(
                user = user,
                tokenType = REFRESH_TOKEN_TYPE,
                issuedAt = issuedAt,
                expiration = refreshTokenExpiration,
            ),
            accessTokenExpiresIn = accessTokenExpiration.seconds,
            refreshTokenExpiresIn = refreshTokenExpiration.seconds,
        )
    }

    override fun parseAccessToken(
        token: String,
    ) = parse(
        token = token,
        expectedType = ACCESS_TOKEN_TYPE,
    )

    override fun parseRefreshToken(
        token: String,
    ) = parse(
        token = token,
        expectedType = REFRESH_TOKEN_TYPE,
    )

    private fun encode(
        user: User,
        tokenType: String,
        issuedAt: Instant,
        expiration: Duration,
    ): String {
        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.email)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(expiration))
            .id(TSID.fast().toString())
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .claim(ROLE_CLAIM, user.role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()

        return jwtEncoder.encode(
            JwtEncoderParameters.from(header, claims),
        ).tokenValue
    }

    private fun parse(
        token: String,
        expectedType: String,
    ): TokenProvider.TokenClaims {
        try {
            val jwt = jwtDecoder.decode(token)
            if (jwt.getClaimAsString(TOKEN_TYPE_CLAIM) != expectedType) {
                throw InvalidTokenException()
            }

            return TokenProvider.TokenClaims(
                subject = jwt.subject ?: throw InvalidTokenException(),
                role = jwt.getClaimAsString(ROLE_CLAIM) ?: throw InvalidTokenException(),
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

    companion object {
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val REFRESH_TOKEN_TYPE = "refresh"
        private const val TOKEN_TYPE_CLAIM = "type"
        private const val ROLE_CLAIM = "role"
    }
}
