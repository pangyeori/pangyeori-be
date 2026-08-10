package com.debate.pangyeori.user.service

import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.dto.SignInResponse
import com.debate.pangyeori.user.exception.InvalidCredentialsException
import com.debate.pangyeori.user.exception.InvalidTokenException
import com.debate.pangyeori.user.repository.RefreshTokenRedisRepository
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
    private val refreshTokenRedisRepository: RefreshTokenRedisRepository,
) {

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    fun refresh(
        refreshToken: String,
    ): SignInResponse {
        val claims = tokenProvider.parseRefreshToken(refreshToken)
        val user = userRepository.findByEmail(
            email = claims.subject,
        )?.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw InvalidTokenException()

        if (!refreshTokenRedisRepository.consume(
                token = refreshToken,
                email = claims.subject,
            )
        ) {
            throw InvalidTokenException()
        }

        return issueAndStore(user)
    }

    fun logout(
        refreshToken: String,
    ) {
        val claims = tokenProvider.parseRefreshToken(refreshToken)

        if (!refreshTokenRedisRepository.consume(
                token = refreshToken,
                email = claims.subject,
            )
        ) {
            throw InvalidTokenException()
        }
    }

    private fun issueAndStore(
        user: User,
    ): SignInResponse {
        val tokens = tokenProvider.issue(user)
        refreshTokenRedisRepository.save(
            token = tokens.refreshToken,
            email = user.email,
            expiresInSeconds = tokens.refreshTokenExpiresIn,
        )

        return SignInResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresIn = tokens.accessTokenExpiresIn,
            refreshTokenExpiresIn = tokens.refreshTokenExpiresIn,
        )
    }
}
