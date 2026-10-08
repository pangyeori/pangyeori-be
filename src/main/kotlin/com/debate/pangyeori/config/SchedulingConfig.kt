package com.debate.pangyeori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean(DEBATE_CONNECTION_SCHEDULER)
    fun debateConnectionScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("debate-connection-")
    }

    companion object {
        const val DEBATE_CONNECTION_SCHEDULER = "debateConnectionScheduler"
    }
}
