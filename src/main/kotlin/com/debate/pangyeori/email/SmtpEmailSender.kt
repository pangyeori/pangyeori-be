package com.debate.pangyeori.email

import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class SmtpEmailSender(
    private val javaMailSender: JavaMailSender,
) : EmailSender {

    @Retryable(includes = [MailException::class], maxRetries = MAX_RETRIES, delay = RETRY_DELAY_MS)
    override fun send(
        to: String,
        subject: String,
        content: String,
    ) {
        val message = SimpleMailMessage()
        message.setTo(to)
        message.subject = subject
        message.text = content
        javaMailSender.send(message)
    }

    companion object {
        private const val MAX_RETRIES = 2L
        private const val RETRY_DELAY_MS = 500L
    }
}
