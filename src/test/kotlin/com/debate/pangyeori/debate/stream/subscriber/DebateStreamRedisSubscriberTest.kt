package com.debate.pangyeori.debate.stream.subscriber

import com.debate.pangyeori.debate.stream.message.DebateStreamEvents
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.connection.DefaultMessage
import tools.jackson.databind.ObjectMapper

class DebateStreamRedisSubscriberTest : BehaviorSpec({
    val objectMapper = mockk<ObjectMapper>()
    val debateSseRegistry = mockk<DebateSseRegistry>(relaxed = true)
    val subscriber = DebateStreamRedisSubscriber(
        objectMapper = objectMapper,
        debateSseRegistry = debateSseRegistry,
    )

    val body = """{"debateId":"0000000000001"}""".toByteArray()
    val redisMessage = DefaultMessage("debate:sse:events".toByteArray(), body)

    beforeEach {
        clearMocks(objectMapper, debateSseRegistry)
    }

    Given("채널 메시지를 받으면") {
        When("역직렬화에 성공하면") {
            Then("로컬 레지스트리로 dispatch 한다") {
                val parsed = DebateStreamMessage(
                    debateId = "0000000000001",
                    target = DebateStreamMessage.Target.all(),
                    eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                    data = mapOf("debateStatus" to "READY"),
                )
                every { objectMapper.readValue(body, DebateStreamMessage::class.java) } returns parsed

                subscriber.onMessage(redisMessage, null)

                verify(exactly = 1) { debateSseRegistry.dispatch(parsed) }
            }
        }

        When("역직렬화에 실패하면") {
            Then("예외를 삼키고 dispatch하지 않는다") {
                every {
                    objectMapper.readValue(body, DebateStreamMessage::class.java)
                } throws RuntimeException("broken payload")

                subscriber.onMessage(redisMessage, null)

                verify(exactly = 0) { debateSseRegistry.dispatch(any()) }
            }
        }
    }
})
