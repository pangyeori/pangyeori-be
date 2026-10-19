package com.debate.pangyeori.email.sender

import org.springframework.context.annotation.Profile
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component

@Component
@Profile("!local")
class SmtpEmailSender(
    private val javaMailSender: JavaMailSender,
) : EmailSender {

    @Retryable(includes = [MailException::class], maxRetries = MAX_RETRIES, delay = RETRY_DELAY_MS)
    override fun send(
        to: String,
        subject: String,
        content: String,
        htmlContent: String,
        inlineImages: List<EmailInlineImage>,
    ) {
        val message = javaMailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, CHARSET)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(content, htmlContent)
        inlineImages.forEach { helper.addInline(it.contentId, it.resource, it.contentType) }
        javaMailSender.send(message)
    }

    companion object {
        private const val CHARSET = "UTF-8"
        private const val MAX_RETRIES = 2L
        private const val RETRY_DELAY_MS = 500L
    }
}
