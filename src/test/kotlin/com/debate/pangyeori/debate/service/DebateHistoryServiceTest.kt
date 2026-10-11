package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.event.DebateHistoryRecordedEvent
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.exception.DebateHistoryAlreadySubmittedException
import com.debate.pangyeori.debate.exception.DebateNotYourTurnException
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class DebateHistoryServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val debateHistoryRepository = mockk<DebateHistoryRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = DebateHistoryService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        debateHistoryRepository = debateHistoryRepository,
        userRepository = userRepository,
        eventPublisher = eventPublisher,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val hostId = "0000000000002"
    val hostEmail = "host@pangyeori.com"
    val now = LocalDateTime.now(ZoneOffset.UTC)

    beforeEach {
        clearMocks(debateRepository, debateUserRepository, debateHistoryRepository, userRepository, eventPublisher)
    }

    fun debate(
        stage: DebateStage = DebateStage.OPENING_PROS,
        currentStageStartedAt: LocalDateTime? = now,
        turnTimeSeconds: Int = 60,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::status, DebateStatus.IN_PROGRESS)
        .set(Debate::currentStage, stage)
        .set(Debate::currentStageStartedAt, currentStageStartedAt)
        .set(Debate::turnTimeSeconds, turnTimeSeconds)
        .sample()

    fun member(
        position: DebatePosition,
        role: DebateUserRole,
        userId: String = hostId,
    ) = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
        .set(DebateUser::user, fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, userId).sample())
        .set(DebateUser::position, position)
        .set(DebateUser::role, role)
        .sample()

    Given("발언을 제출하면") {
        When("내 턴이고 처음 제출하면") {
            Then("발언을 저장하고 발언 기록, 단계 전환 이벤트를 발행한다") {
                val target = debate(stage = DebateStage.OPENING_PROS)
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebatePosition.PROS, DebateUserRole.HOST)
                every { debateHistoryRepository.findByDebateIdAndStage(debateId, DebateStage.OPENING_PROS) } returns null
                every { debateHistoryRepository.save(any()) } answers {
                    setAuditFields(fixtureMonkey.giveMeKotlinBuilder<DebateHistory>().set(DebateHistory::id, "0000000000099").sample())
                }

                service.submit(debateId, hostEmail, "저는 찬성합니다")

                target.currentStage shouldBe DebateStage.OPENING_CONS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        DebateHistoryRecordedEvent(debateId, DebateStage.OPENING_PROS, DebateUserRole.HOST, "저는 찬성합니다"),
                    )
                }
                verify(exactly = 1) {
                    eventPublisher.publishEvent(match<DebateStageChangedEvent> { it.stage == DebateStage.OPENING_CONS })
                }
            }
        }

        When("내 턴이 아니면") {
            Then("DebateNotYourTurnException을 던진다") {
                val target = debate(stage = DebateStage.OPENING_PROS)
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebatePosition.CONS, DebateUserRole.HOST)

                shouldThrow<DebateNotYourTurnException> {
                    service.submit(debateId, hostEmail, "저는 찬성합니다")
                }
            }
        }

        When("같은 단계에 이미 제출했으면") {
            Then("DebateHistoryAlreadySubmittedException을 던진다") {
                val target = debate(stage = DebateStage.OPENING_PROS)
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebatePosition.PROS, DebateUserRole.HOST)
                every { debateHistoryRepository.findByDebateIdAndStage(debateId, DebateStage.OPENING_PROS) } returns
                    fixtureMonkey.giveMeKotlinBuilder<DebateHistory>().sample()

                shouldThrow<DebateHistoryAlreadySubmittedException> {
                    service.submit(debateId, hostEmail, "저는 찬성합니다")
                }
            }
        }
    }

    Given("턴 마감을 지연 검사하면") {
        When("마감 시각이 아직 지나지 않았으면") {
            Then("아무것도 하지 않는다") {
                val target = debate(stage = DebateStage.OPENING_PROS, currentStageStartedAt = now, turnTimeSeconds = 600)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.forceAdvanceIfOverdue(debateId)

                verify(exactly = 0) { debateHistoryRepository.save(any()) }
            }
        }

        When("마감 시각이 지났고 아직 기록이 없으면") {
            Then("빈 발언을 기록하고 다음 단계로 넘긴다") {
                val target = debate(
                    stage = DebateStage.OPENING_PROS,
                    currentStageStartedAt = now.minusSeconds(120),
                    turnTimeSeconds = 60,
                )
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateHistoryRepository.findByDebateIdAndStage(debateId, DebateStage.OPENING_PROS) } returns null
                every { debateUserRepository.findByDebateIdAndPosition(debateId, DebatePosition.PROS) } returns
                    member(DebatePosition.PROS, DebateUserRole.HOST)
                every { debateHistoryRepository.save(any()) } answers {
                    setAuditFields(fixtureMonkey.giveMeKotlinBuilder<DebateHistory>().set(DebateHistory::id, "0000000000099").sample())
                }

                service.forceAdvanceIfOverdue(debateId)

                target.currentStage shouldBe DebateStage.OPENING_CONS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        DebateHistoryRecordedEvent(debateId, DebateStage.OPENING_PROS, DebateUserRole.HOST, null),
                    )
                }
            }
        }

        When("마감 시각이 지났지만 이미 기록이 있으면") {
            Then("다시 처리하지 않는다") {
                val target = debate(
                    stage = DebateStage.OPENING_PROS,
                    currentStageStartedAt = now.minusSeconds(120),
                    turnTimeSeconds = 60,
                )
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateHistoryRepository.findByDebateIdAndStage(debateId, DebateStage.OPENING_PROS) } returns
                    fixtureMonkey.giveMeKotlinBuilder<DebateHistory>().sample()

                service.forceAdvanceIfOverdue(debateId)

                target.currentStage shouldBe DebateStage.OPENING_PROS
                verify(exactly = 0) { debateHistoryRepository.save(any()) }
            }
        }
    }
})
