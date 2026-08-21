package com.debate.pangyeori.auth.service

import com.debate.pangyeori.auth.dto.response.PasswordResetResponse
import com.debate.pangyeori.auth.exception.PasswordResetTokenNotFoundException
import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.auth.repository.PasswordResetRedisRepository
import com.debate.pangyeori.auth.repository.RefreshTokenRepository
import com.debate.pangyeori.common.util.maskEmail
import com.debate.pangyeori.user.exception.EmailNotVerifiedException
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val emailVerificationRedisRepository: EmailVerificationRedisRepository,
    private val passwordResetRedisRepository: PasswordResetRedisRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun issueToken(
        email: String,
    ): PasswordResetResponse {
        val normalizedEmail = email.trim().lowercase()

        if (!emailVerificationRedisRepository.isVerified(
                email = normalizedEmail,
            )
        ) {
            throw EmailNotVerifiedException()
        }
        if (!userRepository.existsByEmail(
                email = normalizedEmail,
            )
        ) {
            throw UserNotFoundException()
        }

        val token = generateToken()
        passwordResetRedisRepository.saveToken(
            email = normalizedEmail,
            token = token,
        )
        emailVerificationRedisRepository.clearVerified(
            email = normalizedEmail,
        )
        logger.info { "비밀번호 재설정 토큰 발급 완료. email=${normalizedEmail.maskEmail()}" }

        return PasswordResetResponse(
            passwordResetToken = token,
        )
    }

    @Transactional
    fun resetPassword(
        passwordResetToken: String,
        newPassword: String,
    ) {
        val email = passwordResetRedisRepository.findEmailByToken(
            token = passwordResetToken,
        ) ?: throw PasswordResetTokenNotFoundException()

        val user = userRepository.findByEmail(
            email = email,
        ) ?: throw UserNotFoundException()

        user.changePassword(
            newPassword = passwordEncoder.encode(newPassword)!!,
        )

        refreshTokenRepository.findAllByUserAndRevokedAtIsNull(
            user = user,
        ).forEach { it.revoke() }

        passwordResetRedisRepository.deleteToken(
            token = passwordResetToken,
        )
        logger.info { "비밀번호 재설정 완료. userId=${user.id}" }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val TOKEN_BYTE_LENGTH = 32
        private val SECURE_RANDOM = SecureRandom()
    }
}
