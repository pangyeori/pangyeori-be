package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateStartedEvent
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateNotReadyException
import com.debate.pangyeori.debate.exception.DebateAlreadyConnectedException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository
import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository.Member
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import com.debate.pangyeori.user.domain.User
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional

class DebateConnectionServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val presenceRepository = mockk<DebatePresenceRedisRepository>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val roomEventPublisher = mockk<RedisDebateConnectionEventPublisher>(relaxed = true)
    val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    val service = DebateConnectionService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        debatePresenceRedisRepository = presenceRepository,
        debateConnectionEventPublisher = roomEventPublisher,
        eventPublisher = eventPublisher,
        taskScheduler = taskScheduler,
        transactionTemplate = TransactionTemplate(mockk<PlatformTransactionManager>(relaxed = true)),
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val hostId = "0000000000002"
    val guestId = "0000000000003"
    val sessionId = "session-1"

    beforeEach {
        clearMocks(
            debateRepository,
            debateUserRepository,
            presenceRepository,
            eventPublisher,
            roomEventPublisher,
            taskScheduler,
        )
    }

    fun readyDebate(status: DebateStatus = DebateStatus.READY) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::status, status)
        .sample()

    fun member(
        userId: String,
        role: DebateUserRole,
        status: DebateUserStatus = DebateUserStatus.ACCEPTED,
        disconnectCount: Int = 0,
    ) = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
        .set(DebateUser::user, fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, userId).sample())
        .set(DebateUser::role, role)
        .set(DebateUser::status, status)
        .set(DebateUser::disconnectCount, disconnectCount)
        .sample()

    Given("입장 검사를 할 때") {
        When("토론 참여자가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.validateEntry(debateId, hostId, sessionId)
                }
            }
        }

        When("토론방 상태가 READY가 아니면") {
            Then("DebateNotReadyException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(hostId, DebateUserRole.HOST)
                every { debateRepository.findById(debateId) } returns Optional.of(readyDebate(DebateStatus.WAITING))

                shouldThrow<DebateNotReadyException> {
                    service.validateEntry(debateId, hostId, sessionId)
                }
            }
        }

        When("토론이 이미 진행 중이면") {
            Then("참여자의 재접속을 허용한다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(hostId, DebateUserRole.HOST)
                every { debateRepository.findById(debateId) } returns Optional.of(readyDebate(DebateStatus.IN_PROGRESS))
                every { presenceRepository.findDebateIdBySession(sessionId) } returns null

                service.validateEntry(debateId, hostId, sessionId)
            }
        }

        When("세션이 이미 다른 토론방에 접속 중이면") {
            Then("DebateAlreadyConnectedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(hostId, DebateUserRole.HOST)
                every { debateRepository.findById(debateId) } returns Optional.of(readyDebate())
                every { presenceRepository.findDebateIdBySession(sessionId) } returns "0000000000009"

                shouldThrow<DebateAlreadyConnectedException> {
                    service.validateEntry(debateId, hostId, sessionId)
                }
            }
        }
    }

    Given("참여자가 입장하면") {
        When("상대방도 이미 입장해 있으면") {
            Then("토론을 시작하고 상태 변경과 시작 이벤트를 발행한다") {
                val debate = readyDebate()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns
                    member(guestId, DebateUserRole.GUEST)
                every { presenceRepository.findMembers(debateId) } returns listOf(
                    Member(hostId, DebateUserRole.HOST),
                    Member(guestId, DebateUserRole.GUEST),
                )
                every { debateRepository.findWithLockById(debateId) } returns debate

                service.enter(debateId, guestId, sessionId)

                debate.status shouldBe DebateStatus.IN_PROGRESS
                debate.currentStage shouldBe DebateStage.OPENING_PROS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(DebateStatusChangedEvent(debateId, DebateStatus.IN_PROGRESS))
                }
                verify(exactly = 1) { eventPublisher.publishEvent(DebateStartedEvent(debateId)) }
            }
        }

        When("상대방이 아직 입장하지 않았으면") {
            Then("토론을 시작하지 않는다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(hostId, DebateUserRole.HOST)
                every { presenceRepository.findMembers(debateId) } returns listOf(
                    Member(hostId, DebateUserRole.HOST),
                )

                service.enter(debateId, hostId, sessionId)

                verify(exactly = 0) { debateRepository.findWithLockById(any()) }
                verify(exactly = 1) {
                    roomEventPublisher.publish(
                        DebateConnectionEvent(debateId, DebateConnectionEvent.Type.ENTERED, DebateUserRole.HOST),
                    )
                }
            }
        }
    }

    Given("진행 중인 토론방에 참여자가 재입장하면") {
        When("토론이 이미 시작되어 있으면") {
            Then("입장 이벤트만 발행하고 토론을 다시 시작하지 않는다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(hostId, DebateUserRole.HOST)
                every { presenceRepository.findMembers(debateId) } returns listOf(
                    Member(hostId, DebateUserRole.HOST),
                    Member(guestId, DebateUserRole.GUEST),
                )
                every { debateRepository.findWithLockById(debateId) } returns readyDebate(DebateStatus.IN_PROGRESS)

                service.enter(debateId, hostId, sessionId)

                verify(exactly = 1) {
                    roomEventPublisher.publish(
                        DebateConnectionEvent(debateId, DebateConnectionEvent.Type.ENTERED, DebateUserRole.HOST),
                    )
                }
                verify(exactly = 0) { eventPublisher.publishEvent(any<DebateStartedEvent>()) }
            }
        }
    }

    Given("참여자가 이탈하면") {
        When("토론방이 READY 상태이면") {
            Then("이탈 이벤트를 발행하고 재접속 유예 후 끊김 횟수를 증가시킨다") {
                val hostMember = member(hostId, DebateUserRole.HOST)
                every { presenceRepository.findDebateIdBySession(sessionId) } returns debateId
                every { presenceRepository.find(debateId, sessionId) } returns Member(hostId, DebateUserRole.HOST)
                every { debateRepository.findById(debateId) } returns Optional.of(readyDebate())
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns hostMember
                every { presenceRepository.findMembers(debateId) } returns emptyList()

                service.leave(sessionId)

                verify(exactly = 1) {
                    roomEventPublisher.publish(
                        DebateConnectionEvent(debateId, DebateConnectionEvent.Type.LEFT, DebateUserRole.HOST),
                    )
                }
                val scheduled = slot<Runnable>()
                verify(exactly = 1) { taskScheduler.schedule(capture(scheduled), any<Instant>()) }

                scheduled.captured.run()

                hostMember.disconnectCount shouldBe 1
            }
        }

        When("재접속하여 다시 입장해 있으면") {
            Then("끊김 횟수를 증가시키지 않는다") {
                val hostMember = member(hostId, DebateUserRole.HOST)
                every { presenceRepository.findDebateIdBySession(sessionId) } returns debateId
                every { presenceRepository.find(debateId, sessionId) } returns Member(hostId, DebateUserRole.HOST)
                every { debateRepository.findById(debateId) } returns Optional.of(readyDebate())
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns hostMember
                every { presenceRepository.findMembers(debateId) } returns listOf(Member(hostId, DebateUserRole.HOST))

                service.leave(sessionId)

                val scheduled = slot<Runnable>()
                verify(exactly = 1) { taskScheduler.schedule(capture(scheduled), any<Instant>()) }

                scheduled.captured.run()

                hostMember.disconnectCount shouldBe 0
            }
        }
    }
})
