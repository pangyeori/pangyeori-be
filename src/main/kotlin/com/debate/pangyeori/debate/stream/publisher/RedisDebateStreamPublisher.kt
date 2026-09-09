package com.debate.pangyeori.debate.stream.publisher

import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class RedisDebateStreamPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : DebateStreamPublisher {
    override fun publish(
        message: DebateStreamMessage,
    ) {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message))
    }

    companion object {
        const val CHANNEL = "debate:sse:events"
    }
}
