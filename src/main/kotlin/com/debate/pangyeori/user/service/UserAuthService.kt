package com.debate.pangyeori.user.service

import com.debate.pangyeori.user.domain.RefreshToken
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.dto.response.SignInResponse
import com.debate.pangyeori.user.exception.InvalidCredentialsException
import com.debate.pangyeori.user.exception.InvalidTokenException
import com.debate.pangyeori.user.repository.RefreshTokenRepository
import com.debate.pangyeori.user.repository.UserRepository
import com.debate.pangyeori.user.token.TokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    fun signIn(
        email: String,
        password: String,
    ): SignInResponse {
        val user = userRepository.findByEmail(
            email = email.trim().lowercase(),
        ) ?: throw InvalidCredentialsException()

        if (user.status != UserStatus.ACTIVE || !passwordEncoder.matches(
                password,
                user.password,
            )
        ) {
            throw InvalidCredentialsException()
        }

        return issueAndStore(user)
    }

    @Transactional
    fun refresh(
        refreshToken: String,
    ): SignInResponse {
        val claims = tokenProvider.parseRefreshToken(refreshToken)
        val savedToken = consume(
            refreshToken = refreshToken,
            subject = claims.subject,
        )
        val user = savedToken.user.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw InvalidTokenException()

        return issueAndStore(user)
    }

    @Transactional
    fun logout(
        refreshToken: String,
    ) {
        val claims = tokenProvider.parseRefreshToken(refreshToken)

        consume(
            refreshToken = refreshToken,
            subject = claims.subject,
        )
    }

    private fun issueAndStore(
        user: User,
    ): SignInResponse {
        val tokens = tokenProvider.issue(user)
        refreshTokenRepository.save(
            RefreshToken.create(
                user = user,
                token = tokens.refreshToken,
                expiresInSeconds = tokens.refreshTokenExpiresIn,
            ),
        )

        return SignInResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshTokenExpiresIn = tokens.refreshTokenExpiresIn,
        )
    }

    private fun consume(
        refreshToken: String,
        subject: String,
    ): RefreshToken {
        val savedToken = refreshTokenRepository.findByTokenHashForUpdate(
            tokenHash = RefreshToken.hash(refreshToken),
        )?.takeIf { it.user.email == subject && it.isAvailable() }
            ?: throw InvalidTokenException()

        savedToken.revoke()

        return savedToken
    }
}
