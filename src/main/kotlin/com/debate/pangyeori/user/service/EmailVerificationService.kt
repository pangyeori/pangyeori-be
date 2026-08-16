package com.debate.pangyeori.user.service

import com.debate.pangyeori.email.EmailSender
import com.debate.pangyeori.email.exception.EmailSendFailedException
import com.debate.pangyeori.user.exception.EmailVerificationAlreadyVerifiedException
import com.debate.pangyeori.user.exception.EmailVerificationCodeMismatchException
import com.debate.pangyeori.user.exception.EmailVerificationCodeNotFoundException
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class EmailVerificationService(
    private val emailVerificationRedisRepository: EmailVerificationRedisRepository,
    private val emailSender: EmailSender,
) {

    private val logger = KotlinLogging.logger {}

    fun sendCode(
        email: String,
    ) {
        val code = generateCode()
        emailVerificationRedisRepository.saveCode(
            email = email,
            code = code,
        )
        try {
            emailSender.send(
                to = email,
                subject = MAIL_SUBJECT,
                content = "인증 코드: $code",
            )
        } catch (e: MailException) {
            logger.warn(e) { "이메일 발송 재시도 초과로 발송에 실패했습니다. email=${maskEmail(email)}" }
            throw EmailSendFailedException()
        }
        logger.info { "이메일 인증 코드 발송 완료. email=${maskEmail(email)}" }
    }

    fun confirmCode(
        email: String,
        code: String,
    ) {
        if (emailVerificationRedisRepository.isVerified(email)) {
            throw EmailVerificationAlreadyVerifiedException()
        }

        val savedCode = emailVerificationRedisRepository.findCode(email)
            ?: throw EmailVerificationCodeNotFoundException()

        if (savedCode != code) {
            throw EmailVerificationCodeMismatchException()
        }

        emailVerificationRedisRepository.deleteCode(email)
        emailVerificationRedisRepository.markVerified(email)
        logger.info { "이메일 인증 완료. email=${maskEmail(email)}" }
    }

    private fun generateCode() = SECURE_RANDOM.nextInt(CODE_UPPER_BOUND).toString().padStart(CODE_LENGTH, '0')

    private fun maskEmail(
        email: String,
    ): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return MASKED_EMAIL_FALLBACK

        val localPart = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val visibleLength = minOf(EMAIL_MASK_VISIBLE_LENGTH, localPart.length)

        return localPart.take(visibleLength) + "*".repeat(localPart.length - visibleLength) + domain
    }

    companion object {
        private const val MAIL_SUBJECT = "[Pangyeori] 이메일 인증 코드"
        private const val CODE_LENGTH = 6
        private const val CODE_UPPER_BOUND = 1_000_000
        private const val EMAIL_MASK_VISIBLE_LENGTH = 2
        private const val MASKED_EMAIL_FALLBACK = "***"
        private val SECURE_RANDOM = SecureRandom()
    }
}
