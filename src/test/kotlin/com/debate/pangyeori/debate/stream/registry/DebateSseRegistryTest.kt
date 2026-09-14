package com.debate.pangyeori.debate.stream.registry

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.stream.message.DebateStreamEvents
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class DebateSseRegistryTest : BehaviorSpec({
    val debateId = "0000000000001"

    fun relaxedEmitter(): SseEmitter {
        val emitter = mockk<SseEmitter>()
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } just runs
        every { emitter.complete() } just runs
        every { emitter.completeWithError(any()) } just runs
        every { emitter.onCompletion(any()) } just runs
        every { emitter.onTimeout(any()) } just runs
        every { emitter.onError(any()) } just runs
        return emitter
    }

    Given("호스트와 게스트가 한 토론방을 구독 중이면") {
        fun setup(): Triple<DebateSseRegistry, SseEmitter, SseEmitter> {
            val registry = DebateSseRegistry()
            val hostEmitter = relaxedEmitter()
            val guestEmitter = relaxedEmitter()
            registry.register(
                debateId = debateId,
                userId = "host-1",
                role = DebateUserRole.HOST,
                emitter = hostEmitter,
            )
            registry.register(
                debateId = debateId,
                userId = "guest-1",
                role = DebateUserRole.GUEST,
                emitter = guestEmitter,
            )
            return Triple(registry, hostEmitter, guestEmitter)
        }

        When("ALL 대상 메시지를 dispatch하면") {
            Then("둘 다에게 전송한다") {
                val (registry, hostEmitter, guestEmitter) = setup()

                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.all(),
                        eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                        data = mapOf("debateStatus" to "READY"),
                    ),
                )

                verify(exactly = 1) { hostEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 1) { guestEmitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }

        When("HOST 대상 메시지를 dispatch하면") {
            Then("호스트에게만 전송한다") {
                val (registry, hostEmitter, guestEmitter) = setup()

                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.host(),
                        eventName = DebateStreamEvents.QUEUE_CHANGED,
                        data = mapOf("requestList" to emptyList<Any>()),
                    ),
                )

                verify(exactly = 1) { hostEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 0) { guestEmitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }

        When("특정 유저 대상 메시지를 dispatch하면") {
            Then("그 유저에게만 전송한다") {
                val (registry, hostEmitter, guestEmitter) = setup()

                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.user(userId = "guest-1"),
                        eventName = DebateStreamEvents.GUEST_STATUS_CHANGED,
                        data = mapOf("guestStatus" to "ACCEPTED"),
                    ),
                )

                verify(exactly = 0) { hostEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 1) { guestEmitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }

        When("close=true 메시지를 dispatch하면") {
            Then("프레임 전송 후 모두 완료하고 이후 dispatch는 무시된다") {
                val (registry, hostEmitter, guestEmitter) = setup()

                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.all(),
                        eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                        data = mapOf("debateStatus" to "READY"),
                        close = true,
                    ),
                )
                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.all(),
                        eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                        data = mapOf("debateStatus" to "FINISHED"),
                    ),
                )

                verify(exactly = 1) { hostEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 1) { guestEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                verify(exactly = 1) { hostEmitter.complete() }
                verify(exactly = 1) { guestEmitter.complete() }
            }
        }

        When("연결이 제거된 뒤 dispatch하면") {
            Then("전송하지 않는다") {
                val registry = DebateSseRegistry()
                val emitter = relaxedEmitter()
                val subscriptionId = registry.register(
                    debateId = debateId,
                    userId = "guest-1",
                    role = DebateUserRole.GUEST,
                    emitter = emitter,
                )
                registry.remove(
                    debateId = debateId,
                    subscriptionId = subscriptionId,
                )

                registry.dispatch(
                    DebateStreamMessage(
                        debateId = debateId,
                        target = DebateStreamMessage.Target.all(),
                        eventName = DebateStreamEvents.DEBATE_STATUS_CHANGED,
                        data = mapOf("debateStatus" to "READY"),
                    ),
                )

                verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }
    }

    Given("연결 직후") {
        When("sendSnapshot을 호출하면") {
            Then("해당 구독에 스냅샷 프레임을 보낸다") {
                val registry = DebateSseRegistry()
                val emitter = relaxedEmitter()
                val subscriptionId = registry.register(
                    debateId = debateId,
                    userId = "guest-1",
                    role = DebateUserRole.GUEST,
                    emitter = emitter,
                )

                val sent = registry.sendSnapshot(
                    debateId = debateId,
                    subscriptionId = subscriptionId,
                    payload = mapOf("debateStatus" to "WAITING"),
                )

                sent shouldBe true
                verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
            }
        }
    }
})
