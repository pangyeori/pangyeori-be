package com.debate.pangyeori.notification.event.handler

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateGuestStatusChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.DebateNotFoundException
import com.debate.pangyeori.debate.exception.UserNotFoundInQueueException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.notification.message.NotificationMessages
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.user.domain.User
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.util.*

private const val DEBATE_ID = "0000000000010"

class NotificationEventHandlerTest : BehaviorSpec({
    val notificationRepository = mockk<NotificationRepository>()
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val handler = NotificationEventHandler(
        notificationRepository = notificationRepository,
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    beforeEach {
        clearMocks(notificationRepository, debateRepository, debateUserRepository)
    }

    val host = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000001")
        .set(User::nickname, "호스트")
        .sample()
    val guest = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000002")
        .set(User::nickname, "게스트")
        .sample()

    fun debate(
        guestUser: User? = null,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, DEBATE_ID)
        .set(Debate::host, host)
        .set(Debate::guest, guestUser)
        .set(Debate::title, "테스트 토론")
        .sample()

    fun debateUser(
        user: User,
    ) = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
        .set(DebateUser::user, user)
        .sample()

    Given("게스트 상태 변경 이벤트를 받으면") {
        When("ACCEPTED면") {
            Then("수락된 게스트에게 QUEUE_REQUEST_ACCEPTED 알림을 저장한다") {
                val targetDebate = debate(guestUser = guest)
                val member = debateUser(guest)
                val captured = slot<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns member
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        status = DebateUserStatus.ACCEPTED,
                    ),
                )

                captured.captured.recipient.id shouldBe guest.id
                captured.captured.debate shouldBe targetDebate
                captured.captured.type shouldBe NotificationType.QUEUE_REQUEST_ACCEPTED
                captured.captured.message shouldBe NotificationMessages.build(
                    type = NotificationType.QUEUE_REQUEST_ACCEPTED,
                    debate = targetDebate,
                )
            }
        }

        When("REJECTED면") {
            Then("거절된 요청자에게 QUEUE_REQUEST_REJECTED 알림을 저장한다") {
                val targetDebate = debate()
                val member = debateUser(guest)
                val captured = slot<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns member
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        status = DebateUserStatus.REJECTED,
                    ),
                )

                captured.captured.recipient.id shouldBe guest.id
                captured.captured.type shouldBe NotificationType.QUEUE_REQUEST_REJECTED
                captured.captured.message shouldBe NotificationMessages.build(
                    type = NotificationType.QUEUE_REQUEST_REJECTED,
                    debate = targetDebate,
                )
            }
        }

        When("PENDING이나 CANCELLED면") {
            Then("토론방·요청자 조회 없이 알림을 생성하지 않는다") {
                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        status = DebateUserStatus.PENDING,
                    ),
                )
                handler.handleGuestStatusChanged(
                    DebateGuestStatusChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        status = DebateUserStatus.CANCELLED,
                    ),
                )

                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { debateRepository.findById(any()) }
            }
        }

        When("토론방을 찾을 수 없으면") {
            Then("DebateNotFoundException을 던지고 알림을 생성하지 않는다") {
                every { debateRepository.findById(DEBATE_ID) } returns Optional.empty()

                shouldThrow<DebateNotFoundException> {
                    handler.handleGuestStatusChanged(
                        DebateGuestStatusChangedEvent(
                            debateId = DEBATE_ID,
                            userId = guest.id!!,
                            status = DebateUserStatus.ACCEPTED,
                        ),
                    )
                }

                verify(exactly = 0) { debateUserRepository.findByDebateIdAndUserId(any(), any()) }
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("대기열에서 요청자를 찾을 수 없으면") {
            Then("UserNotFoundInQueueException을 던지고 알림을 생성하지 않는다") {
                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(debate(guestUser = guest))
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns null

                shouldThrow<UserNotFoundInQueueException> {
                    handler.handleGuestStatusChanged(
                        DebateGuestStatusChangedEvent(
                            debateId = DEBATE_ID,
                            userId = guest.id!!,
                            status = DebateUserStatus.ACCEPTED,
                        ),
                    )
                }

                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("알림 저장 중 예외가 발생하면") {
            Then("예외를 삼키지 않고 그대로 전파한다") {
                val targetDebate = debate(guestUser = guest)
                val member = debateUser(guest)

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns member
                every { notificationRepository.save(any()) } throws IllegalStateException("DB 저장 실패")

                shouldThrow<IllegalStateException> {
                    handler.handleGuestStatusChanged(
                        DebateGuestStatusChangedEvent(
                            debateId = DEBATE_ID,
                            userId = guest.id!!,
                            status = DebateUserStatus.ACCEPTED,
                        ),
                    )
                }
            }
        }
    }

    Given("대기열 변경 이벤트를 받으면") {
        When("ADD면") {
            Then("호스트에게 QUEUE_REQUEST_ADDED 알림을 저장한다") {
                val targetDebate = debate()
                val member = debateUser(guest)
                val captured = slot<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns member
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleQueueChanged(
                    DebateQueueChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        operation = DebateQueueOperation.ADD,
                    ),
                )

                captured.captured.recipient.id shouldBe host.id
                captured.captured.type shouldBe NotificationType.QUEUE_REQUEST_ADDED
                captured.captured.message shouldBe NotificationMessages.build(
                    type = NotificationType.QUEUE_REQUEST_ADDED,
                    debate = targetDebate,
                    requesterNickname = guest.nickname,
                )
            }
        }

        When("REMOVE면") {
            Then("호스트에게 QUEUE_REQUEST_REMOVED 알림을 저장한다") {
                val targetDebate = debate()
                val member = debateUser(guest)
                val captured = slot<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns member
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleQueueChanged(
                    DebateQueueChangedEvent(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                        operation = DebateQueueOperation.REMOVE,
                    ),
                )

                captured.captured.recipient.id shouldBe host.id
                captured.captured.type shouldBe NotificationType.QUEUE_REQUEST_REMOVED
                captured.captured.message shouldBe NotificationMessages.build(
                    type = NotificationType.QUEUE_REQUEST_REMOVED,
                    debate = targetDebate,
                    requesterNickname = guest.nickname,
                )
            }
        }

        When("CLEAR면") {
            Then("토론방 조회 없이 알림을 생성하지 않는다") {
                handler.handleQueueChanged(
                    DebateQueueChangedEvent(
                        debateId = DEBATE_ID,
                        userId = null,
                        operation = DebateQueueOperation.CLEAR,
                    ),
                )

                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { debateRepository.findById(any()) }
            }
        }

        When("토론방을 찾을 수 없으면") {
            Then("DebateNotFoundException을 던지고 알림을 생성하지 않는다") {
                every { debateRepository.findById(DEBATE_ID) } returns Optional.empty()

                shouldThrow<DebateNotFoundException> {
                    handler.handleQueueChanged(
                        DebateQueueChangedEvent(
                            debateId = DEBATE_ID,
                            userId = guest.id!!,
                            operation = DebateQueueOperation.ADD,
                        ),
                    )
                }

                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("대기열에서 요청자를 찾을 수 없으면") {
            Then("UserNotFoundInQueueException을 던지고 알림을 생성하지 않는다") {
                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(debate())
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = DEBATE_ID,
                        userId = guest.id!!,
                    )
                } returns null

                shouldThrow<UserNotFoundInQueueException> {
                    handler.handleQueueChanged(
                        DebateQueueChangedEvent(
                            debateId = DEBATE_ID,
                            userId = guest.id!!,
                            operation = DebateQueueOperation.REMOVE,
                        ),
                    )
                }

                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("토론방 상태 변경 이벤트를 받으면") {
        When("CANCELLED이고 매칭된 게스트가 있으면") {
            Then("대기열 조회 없이 게스트에게만 DEBATE_CANCELLED 알림을 저장한다") {
                val targetDebate = debate(guestUser = guest)
                val captured = slot<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleStatusChanged(
                    DebateStatusChangedEvent(
                        debateId = DEBATE_ID,
                        status = DebateStatus.CANCELLED,
                    ),
                )

                captured.captured.recipient.id shouldBe guest.id
                captured.captured.type shouldBe NotificationType.DEBATE_CANCELLED
                captured.captured.message shouldBe NotificationMessages.build(
                    type = NotificationType.DEBATE_CANCELLED,
                    debate = targetDebate,
                )
                verify(exactly = 1) { notificationRepository.save(any()) }
                verify(exactly = 0) { debateUserRepository.findAllByDebateIdAndStatus(any(), any()) }
            }
        }

        When("CANCELLED이고 아직 매칭 전이면") {
            Then("대기 중인 참여 요청자 전체에게 각각 DEBATE_CANCELLED 알림을 저장한다") {
                val targetDebate = debate()
                val firstPendingGuest = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000003")
                    .sample()
                val secondPendingGuest = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000004")
                    .sample()
                val captured = mutableListOf<Notification>()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findAllByDebateIdAndStatus(
                        debateId = DEBATE_ID,
                        status = DebateUserStatus.PENDING,
                    )
                } returns listOf(debateUser(firstPendingGuest), debateUser(secondPendingGuest))
                every { notificationRepository.save(capture(captured)) } returns mockk()

                handler.handleStatusChanged(
                    DebateStatusChangedEvent(
                        debateId = DEBATE_ID,
                        status = DebateStatus.CANCELLED,
                    ),
                )

                captured.map { it.recipient.id } shouldContainExactlyInAnyOrder listOf(
                    firstPendingGuest.id,
                    secondPendingGuest.id,
                )
                captured.forEach {
                    it.type shouldBe NotificationType.DEBATE_CANCELLED
                }
            }
        }

        When("CANCELLED이고 매칭 전이면서 대기 중인 요청자도 없으면") {
            Then("알림을 생성하지 않는다") {
                val targetDebate = debate()

                every { debateRepository.findById(DEBATE_ID) } returns Optional.of(targetDebate)
                every {
                    debateUserRepository.findAllByDebateIdAndStatus(
                        debateId = DEBATE_ID,
                        status = DebateUserStatus.PENDING,
                    )
                } returns emptyList()

                shouldNotThrowAny {
                    handler.handleStatusChanged(
                        DebateStatusChangedEvent(
                            debateId = DEBATE_ID,
                            status = DebateStatus.CANCELLED,
                        ),
                    )
                }

                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("CANCELLED가 아니면") {
            Then("토론방 조회 없이 알림을 생성하지 않는다") {
                handler.handleStatusChanged(
                    DebateStatusChangedEvent(
                        debateId = DEBATE_ID,
                        status = DebateStatus.READY,
                    ),
                )

                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { debateRepository.findById(any()) }
            }
        }

        When("토론방을 찾을 수 없으면") {
            Then("DebateNotFoundException을 던지고 알림을 생성하지 않는다") {
                every { debateRepository.findById(DEBATE_ID) } returns Optional.empty()

                shouldThrow<DebateNotFoundException> {
                    handler.handleStatusChanged(
                        DebateStatusChangedEvent(
                            debateId = DEBATE_ID,
                            status = DebateStatus.CANCELLED,
                        ),
                    )
                }

                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("호스트가 대기 중인 요청자 중 한 명을 수락하면 (acceptGuest 흐름을 재현하면)") {
        Then("수락된 게스트만 ACCEPTED, 나머지 요청자는 각각 REJECTED 알림을 받고 호스트·CLEAR·READY는 알림을 만들지 않는다") {
            val winner = fixtureMonkey.giveMeKotlinBuilder<User>()
                .set(User::id, "0000000000005")
                .set(User::nickname, "수락된게스트")
                .sample()
            val firstLoser = fixtureMonkey.giveMeKotlinBuilder<User>()
                .set(User::id, "0000000000006")
                .set(User::nickname, "거절된게스트1")
                .sample()
            val secondLoser = fixtureMonkey.giveMeKotlinBuilder<User>()
                .set(User::id, "0000000000007")
                .set(User::nickname, "거절된게스트2")
                .sample()
            val finalDebate = debate(guestUser = winner)
            val captured = mutableListOf<Notification>()

            every { debateRepository.findById(DEBATE_ID) } returns Optional.of(finalDebate)
            every {
                debateUserRepository.findByDebateIdAndUserId(
                    debateId = DEBATE_ID,
                    userId = winner.id!!,
                )
            } returns debateUser(winner)
            every {
                debateUserRepository.findByDebateIdAndUserId(
                    debateId = DEBATE_ID,
                    userId = firstLoser.id!!,
                )
            } returns debateUser(firstLoser)
            every {
                debateUserRepository.findByDebateIdAndUserId(
                    debateId = DEBATE_ID,
                    userId = secondLoser.id!!,
                )
            } returns debateUser(secondLoser)
            every { notificationRepository.save(capture(captured)) } returns mockk()

            handler.handleGuestStatusChanged(
                DebateGuestStatusChangedEvent(
                    debateId = DEBATE_ID,
                    userId = winner.id!!,
                    status = DebateUserStatus.ACCEPTED,
                ),
            )
            handler.handleGuestStatusChanged(
                DebateGuestStatusChangedEvent(
                    debateId = DEBATE_ID,
                    userId = firstLoser.id!!,
                    status = DebateUserStatus.REJECTED,
                ),
            )
            handler.handleGuestStatusChanged(
                DebateGuestStatusChangedEvent(
                    debateId = DEBATE_ID,
                    userId = secondLoser.id!!,
                    status = DebateUserStatus.REJECTED,
                ),
            )
            handler.handleQueueChanged(
                DebateQueueChangedEvent(
                    debateId = DEBATE_ID,
                    userId = null,
                    operation = DebateQueueOperation.CLEAR,
                ),
            )
            handler.handleStatusChanged(
                DebateStatusChangedEvent(
                    debateId = DEBATE_ID,
                    status = DebateStatus.READY,
                ),
            )

            captured shouldHaveSize 3
            captured.first { it.recipient.id == winner.id }.type shouldBe NotificationType.QUEUE_REQUEST_ACCEPTED
            captured.first { it.recipient.id == firstLoser.id }.type shouldBe NotificationType.QUEUE_REQUEST_REJECTED
            captured.first { it.recipient.id == secondLoser.id }.type shouldBe NotificationType.QUEUE_REQUEST_REJECTED
            captured.map { it.recipient.id } shouldNotContain host.id
        }
    }
})
