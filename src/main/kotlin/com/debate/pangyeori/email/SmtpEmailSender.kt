package com.debate.pangyeori.email

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class SmtpEmailSender(
    private val javaMailSender: JavaMailSender,
) : EmailSender {

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
}
