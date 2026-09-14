package com.debate.pangyeori.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    private val logger = KotlinLogging.logger {}

    @Bean(DEBATE_SSE_EXECUTOR)
    fun debateSseExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.queueCapacity = QUEUE_CAPACITY
        executor.setThreadNamePrefix("debate-sse-")
        executor.setRejectedExecutionHandler { _, _ ->
            logger.warn { "SSE 이벤트 발행 작업이 큐 포화로 버려졌습니다." }
        }
        executor.initialize()
        return executor
    }

    companion object {
        const val DEBATE_SSE_EXECUTOR = "debateSseExecutor"
        private const val QUEUE_CAPACITY = 200
    }
}
