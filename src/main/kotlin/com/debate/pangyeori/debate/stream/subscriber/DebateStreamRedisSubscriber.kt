package com.debate.pangyeori.debate.stream.subscriber

import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class DebateStreamRedisSubscriber(
    private val objectMapper: ObjectMapper,
    private val debateSseRegistry: DebateSseRegistry,
) : MessageListener {
    
    private val logger = KotlinLogging.logger {}

    override fun onMessage(
        message: Message,
        pattern: ByteArray?,
    ) {
        runCatching {
            val parsed = objectMapper.readValue(message.body, DebateStreamMessage::class.java)
            debateSseRegistry.dispatch(parsed)
        }.onFailure {
            logger.warn(it) { "SSE 이벤트 메시지 처리에 실패했습니다." }
        }
    }
}
