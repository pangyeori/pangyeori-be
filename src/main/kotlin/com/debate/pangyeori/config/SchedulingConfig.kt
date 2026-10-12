package com.debate.pangyeori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean(DEBATE_CONNECTION_SCHEDULER)
    fun debateConnectionScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("debate-connection-")
    }

    @Bean(DEBATE_VERDICT_EXECUTOR)
    fun debateVerdictExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        setThreadNamePrefix("debate-verdict-")
    }

    companion object {
        const val DEBATE_CONNECTION_SCHEDULER = "debateConnectionScheduler"
        const val DEBATE_VERDICT_EXECUTOR = "debateVerdictExecutor"
    }
}
