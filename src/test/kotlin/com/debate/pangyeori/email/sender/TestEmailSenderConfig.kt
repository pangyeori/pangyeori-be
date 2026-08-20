package com.debate.pangyeori.email.sender

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestEmailSenderConfig {

    @Bean
    @Primary
    fun emailSender(): EmailSender = FakeEmailSender()
}
