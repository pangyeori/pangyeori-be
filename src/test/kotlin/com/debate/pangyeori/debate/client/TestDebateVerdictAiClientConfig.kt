package com.debate.pangyeori.debate.client

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestDebateVerdictAiClientConfig {

    @Bean
    @Primary
    fun debateVerdictAiClient(): DebateVerdictAiClient = FakeDebateVerdictAiClient()
}
