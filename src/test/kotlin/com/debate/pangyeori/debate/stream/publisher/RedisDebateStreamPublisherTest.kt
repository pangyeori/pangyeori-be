package com.debate.pangyeori.debate.stream.publisher

import com.debate.pangyeori.debate.stream.message.DebateStreamEvents
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper

class RedisDebateStreamPublisherTest : BehaviorSpec({
    val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
    val objectMapper = mockk<ObjectMapper>()
    val publisher = RedisDebateStreamPublisher(
        redisTemplate = redisTemplate,
        objectMapper = objectMapper,
    )

    Given("SSE 메시지를 발행하면") {
        When("직렬화한 뒤") {
            Then("고정 채널로 convertAndSend 한다") {
                val message = DebateStreamMessage(
                    debateId = "0000000000001",
                    target = DebateStreamMessage.Target.all(),
                    eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                    data = mapOf("debateStatus" to "READY"),
                )
                every { objectMapper.writeValueAsString(message) } returns SERIALIZED

                publisher.publish(message)

                verify(exactly = 1) {
                    redisTemplate.convertAndSend(RedisDebateStreamPublisher.CHANNEL, SERIALIZED)
                }
            }
        }
    }
}) {
    companion object {
        private const val SERIALIZED = """{"debateId":"0000000000001"}"""
    }
}
