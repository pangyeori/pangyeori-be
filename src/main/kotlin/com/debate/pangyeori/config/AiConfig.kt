package com.debate.pangyeori.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AiConfig {

    @Bean
    fun chatClient(
        builder: ChatClient.Builder,
    ): ChatClient = builder.build()

    @Bean
    fun openAiHttpClientBuilderCustomizer(): OpenAiHttpClientBuilderCustomizer =
        OpenAiHttpClientBuilderCustomizer { it.timeout(REQUEST_TIMEOUT) }

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
