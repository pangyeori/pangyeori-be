package com.debate.pangyeori.debate.websocket.publisher

import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class RedisDebateConnectionEventPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun publish(
        event: DebateConnectionEvent,
    ) {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event))
    }

    companion object {
        const val CHANNEL = "debate:ws:events"
    }
}
