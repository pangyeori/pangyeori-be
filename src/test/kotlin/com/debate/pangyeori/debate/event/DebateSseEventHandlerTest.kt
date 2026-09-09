package com.debate.pangyeori.debate.event

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.stream.message.DebateStatusChangedPayload
import com.debate.pangyeori.debate.stream.message.DebateStreamEvents
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import com.debate.pangyeori.debate.stream.message.GuestStatusChangedPayload
import com.debate.pangyeori.debate.stream.message.QueueChangedPayload
import com.debate.pangyeori.debate.stream.publisher.DebateStreamPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class DebateSseEventHandlerTest : BehaviorSpec({
    val debateStreamPublisher = mockk<DebateStreamPublisher>(relaxed = true)
    val debateParticipationService = mockk<DebateParticipationService>()
    val handler = DebateSseEventHandler(
        debateStreamPublisher = debateStreamPublisher,
        debateParticipationService = debateParticipationService,
    )

    beforeEach {
        clearMocks(debateStreamPublisher, debateParticipationService)
    }

    Given("토론방 상태 변경 이벤트를 받으면") {
        When("상태가 WAITING이 아니면") {
            Then("전체 대상 메시지를 close=true로 발행한다") {
                val captured = slot<DebateStreamMessage>()
                every { debateStreamPublisher.publish(capture(captured)) } returns Unit

                handler.handleStatusChanged(
                    DebateStatusChangedEvent(
                        debateId = "0000000000001",
                        status = DebateStatus.READY,
                    ),
                )

                captured.captured.debateId shouldBe "0000000000001"
                captured.captured.target.type shouldBe DebateStreamMessage.Target.Type.ALL
                captured.captured.eventName shouldBe DebateStreamEvents.DEBATE_STATUS_CHANGED
                captured.captured.data shouldBe DebateStatusChangedPayload(debateStatus = DebateStatus.READY)
                captured.captured.close shouldBe true
            }
        }

        When("상태가 WAITING이면") {
            Then("close=false로 발행한다") {
                val captured = slot<DebateStreamMessage>()
                every { debateStreamPublisher.publish(capture(captured)) } returns Unit

                handler.handleStatusChanged(
                    DebateStatusChangedEvent(
                        debateId = "0000000000002",
                        status = DebateStatus.WAITING,
                    ),
                )

                captured.captured.close shouldBe false
            }
        }
    }

    Given("대기열 변경 이벤트를 받으면") {
        When("발행하면") {
            Then("호스트 대상으로 현재 대기 목록을 담아 발행한다") {
                every {
                    debateParticipationService.getPendingRequestList(debateId = "0000000000003")
                } returns emptyList()
                val captured = slot<DebateStreamMessage>()
                every { debateStreamPublisher.publish(capture(captured)) } returns Unit

                handler.handleQueueChanged(
                    DebateQueueChangedEvent(
                        debateId = "0000000000003",
                        userId = "0000000000009",
                        operation = DebateQueueOperation.ADD,
                    ),
                )

                captured.captured.target.type shouldBe DebateStreamMessage.Target.Type.HOST
                captured.captured.eventName shouldBe DebateStreamEvents.QUEUE_CHANGED
                captured.captured.data shouldBe QueueChangedPayload(requestList = emptyList())
            }
        }
    }

    Given("게스트 상태 변경 이벤트를 받으면") {
        When("발행하면") {
            Then("해당 게스트 대상으로 발행한다") {
                val captured = slot<DebateStreamMessage>()
                every { debateStreamPublisher.publish(capture(captured)) } returns Unit

                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = "0000000000004",
                        userId = "0000000000010",
                        status = DebateUserStatus.REJECTED,
                    ),
                )

                captured.captured.target.type shouldBe DebateStreamMessage.Target.Type.USER
                captured.captured.target.userId shouldBe "0000000000010"
                captured.captured.eventName shouldBe DebateStreamEvents.GUEST_STATUS_CHANGED
                captured.captured.data shouldBe GuestStatusChangedPayload(guestStatus = DebateUserStatus.REJECTED)
            }
        }
    }

    Given("발행이 실패해도") {
        When("핸들러가 예외를 삼키면") {
            Then("호출자에게 전파하지 않는다") {
                every { debateStreamPublisher.publish(any()) } throws RuntimeException("redis down")

                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = "0000000000005",
                        userId = "0000000000013",
                        status = DebateUserStatus.ACCEPTED,
                    ),
                )

                verify { debateStreamPublisher.publish(any()) }
            }
        }
    }
})
