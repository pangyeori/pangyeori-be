package com.debate.pangyeori.email.sender

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local")
class LoggingEmailSender : EmailSender {

    private val logger = KotlinLogging.logger {}

    override fun send(
        to: String,
        subject: String,
        content: String,
    ) {
        logger.info { "[local] 이메일 발송 생략. to=$to, subject=$subject, content=$content" }
    }
}
