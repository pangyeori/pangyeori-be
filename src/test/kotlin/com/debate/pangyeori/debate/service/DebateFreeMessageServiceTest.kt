package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.event.DebateFreeMessageSentEvent
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateNotFreeStageException
import com.debate.pangyeori.debate.repository.DebateFreeMessageRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
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

class DebateFreeMessageServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val debateFreeMessageRepository = mockk<DebateFreeMessageRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = DebateFreeMessageService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        debateFreeMessageRepository = debateFreeMessageRepository,
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
        clearMocks(debateRepository, debateUserRepository, debateFreeMessageRepository, userRepository, eventPublisher)
    }

    fun debate(
        stage: DebateStage,
        currentStageStartedAt: LocalDateTime? = now,
        freeDebateTimeSeconds: Int = 300,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::status, DebateStatus.IN_PROGRESS)
        .set(Debate::currentStage, stage)
        .set(Debate::currentStageStartedAt, currentStageStartedAt)
        .set(Debate::freeDebateTimeSeconds, freeDebateTimeSeconds)
        .sample()

    fun member(
        role: DebateUserRole,
        userId: String = hostId,
    ) = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
        .set(DebateUser::user, fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, userId).sample())
        .set(DebateUser::role, role)
        .sample()

    Given("자유 토론 메시지를 제출하면") {
        When("자유 토론 단계이고 토론방 멤버면") {
            Then("메시지를 저장하고 실시간 전달 이벤트를 발행한다") {
                val target = debate(stage = DebateStage.FREE)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateFreeMessageRepository.save(any()) } answers {
                    setAuditFields(
                        fixtureMonkey.giveMeKotlinBuilder<DebateFreeMessage>()
                            .set(DebateFreeMessage::id, "0000000000099")
                            .set(DebateFreeMessage::role, DebateUserRole.HOST)
                            .set(DebateFreeMessage::content, "안녕하세요")
                            .sample(),
                        createdAt = now,
                    )
                }

                val response = service.submit(debateId = debateId, userId = hostId, content = "안녕하세요")

                response.content shouldBe "안녕하세요"
                response.role shouldBe DebateUserRole.HOST
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateFreeMessageSentEvent> {
                            it.debateId == debateId && it.role == DebateUserRole.HOST && it.content == "안녕하세요"
                        },
                    )
                }
            }
        }

        When("자유 토론 단계가 아니면") {
            Then("DebateNotFreeStageException을 던진다") {
                val target = debate(stage = DebateStage.VERDICT_2)
                every { debateRepository.findWithLockById(debateId) } returns target

                shouldThrow<DebateNotFreeStageException> {
                    service.submit(debateId = debateId, userId = hostId, content = "안녕하세요")
                }
            }
        }

        When("토론방 멤버가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                val target = debate(stage = DebateStage.FREE)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.submit(debateId = debateId, userId = hostId, content = "안녕하세요")
                }
            }
        }
    }

    Given("자유 토론 마감과 메시지 제출이 겹치면") {
        Then("마감 시각이 지난 메시지를 저장하지 않는다") {
            val target = debate(
                stage = DebateStage.FREE,
                currentStageStartedAt = now.minusSeconds(600),
                freeDebateTimeSeconds = 300,
            )
            every { debateRepository.findWithLockById(debateId) } returns target

            shouldThrow<DebateNotFreeStageException> {
                service.submit(
                    debateId = debateId,
                    userId = hostId,
                    content = "마감 후 발언",
                )
            }

            verify(exactly = 0) { debateFreeMessageRepository.save(any()) }
        }
    }

    Given("자유 토론 메시지를 조회하면") {
        When("토론방 멤버가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.getMessages(debateId = debateId, userEmail = hostEmail, cursor = null, pageSize = null)
                }
            }
        }

        When("사용자를 찾을 수 없으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(hostEmail) } returns null

                shouldThrow<UserNotFoundException> {
                    service.getMessages(debateId = debateId, userEmail = hostEmail, cursor = null, pageSize = null)
                }
            }
        }

        When("메시지가 페이지 크기보다 많으면") {
            Then("hasNext가 true이고 다음 커서를 내려준다") {
                val target = debate(stage = DebateStage.FREE, currentStageStartedAt = now)
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateRepository.findWithLockById(debateId) } returns target

                val messages = (1..3).map { i ->
                    setAuditFields(
                        fixtureMonkey.giveMeKotlinBuilder<DebateFreeMessage>()
                            .set(DebateFreeMessage::id, "000000000009$i")
                            .set(DebateFreeMessage::role, DebateUserRole.HOST)
                            .set(DebateFreeMessage::content, "메시지 $i")
                            .sample(),
                        createdAt = now,
                    )
                }
                every {
                    debateFreeMessageRepository.findAllByDebateId(debateId, null, 3)
                } returns messages

                val page = service.getMessages(debateId = debateId, userEmail = hostEmail, cursor = null, pageSize = 2)

                page.items.size shouldBe 2
                page.hasNext shouldBe true
                page.nextCursor shouldBe page.items.last().id
            }
        }
    }

    Given("자유 토론 마감을 지연 검사하면") {
        When("자유 토론 단계가 아니면") {
            Then("아무것도 하지 않는다") {
                val target = debate(stage = DebateStage.VERDICT_2)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.forceEndIfOverdue(debateId = debateId)

                target.currentStage shouldBe DebateStage.VERDICT_2
                verify(exactly = 0) { eventPublisher.publishEvent(any<DebateStageChangedEvent>()) }
            }
        }

        When("마감 시각이 아직 지나지 않았으면") {
            Then("아무것도 하지 않는다") {
                val target = debate(stage = DebateStage.FREE, currentStageStartedAt = now, freeDebateTimeSeconds = 600)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.forceEndIfOverdue(debateId = debateId)

                target.currentStage shouldBe DebateStage.FREE
            }
        }

        When("마감 시각이 지났으면") {
            Then("최종 판결 단계로 전환하고 이벤트를 발행한다") {
                val target = debate(
                    stage = DebateStage.FREE,
                    currentStageStartedAt = now.minusSeconds(600),
                    freeDebateTimeSeconds = 300,
                )
                every { debateRepository.findWithLockById(debateId) } returns target

                service.forceEndIfOverdue(debateId = debateId)

                target.currentStage shouldBe DebateStage.FINAL_VERDICT
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateStageChangedEvent> { it.stage == DebateStage.FINAL_VERDICT },
                    )
                }
            }
        }
    }

    Given("양측 합의로 자유 토론을 조기 종료하면") {
        When("자유 토론 단계가 아니면") {
            Then("아무것도 하지 않는다") {
                val target = debate(stage = DebateStage.VERDICT_2)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.endByAgreement(debateId = debateId)

                target.currentStage shouldBe DebateStage.VERDICT_2
                verify(exactly = 0) { eventPublisher.publishEvent(any<DebateStageChangedEvent>()) }
            }
        }

        When("자유 토론 단계이면") {
            Then("마감 시각과 무관하게 최종 판결 단계로 전환하고 이벤트를 발행한다") {
                val target = debate(stage = DebateStage.FREE, currentStageStartedAt = now, freeDebateTimeSeconds = 600)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.endByAgreement(debateId = debateId)

                target.currentStage shouldBe DebateStage.FINAL_VERDICT
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateStageChangedEvent> { it.stage == DebateStage.FINAL_VERDICT },
                    )
                }
            }
        }
    }
})
