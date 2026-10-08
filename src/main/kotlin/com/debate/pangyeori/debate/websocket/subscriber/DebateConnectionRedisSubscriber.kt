package com.debate.pangyeori.debate.websocket.subscriber

import com.debate.pangyeori.debate.websocket.DebateDestination
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class DebateConnectionRedisSubscriber(
    private val objectMapper: ObjectMapper,
    private val simpMessagingTemplate: SimpMessagingTemplate,
) : MessageListener {

    private val logger = KotlinLogging.logger {}

    override fun onMessage(
        message: Message,
        pattern: ByteArray?,
    ) {
        runCatching {
            val event = objectMapper.readValue(message.body, DebateConnectionEvent::class.java)
            simpMessagingTemplate.convertAndSend(
                DebateDestination.of(event.debateId),
                event,
            )
        }.onFailure {
            logger.warn(it) { "토론방 WS 이벤트 메시지 처리에 실패했습니다." }
        }
    }
}
