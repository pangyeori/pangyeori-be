package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.event.DebateHistoryRecordedEvent
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.exception.DebateHistoryAlreadySubmittedException
import com.debate.pangyeori.debate.exception.DebateNotYourTurnException
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
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
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class DebateHistoryServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val debateHistoryRepository = mockk<DebateHistoryRepository>()
    val debateVerdictRepository = mockk<DebateVerdictRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = DebateHistoryService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        debateHistoryRepository = debateHistoryRepository,
        debateVerdictRepository = debateVerdictRepository,
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
        clearMocks(debateRepository, debateUserRepository, debateHistoryRepository, debateVerdictRepository, userRepository, eventPublisher)
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
                    setAuditFields(
                        fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                            .set(DebateHistory::id, "0000000000099")
                            .set(DebateHistory::stage, DebateStage.OPENING_PROS)
                            .set(DebateHistory::content, "저는 찬성합니다")
                            .sample(),
                        createdAt = now,
                    )
                }

                service.submit(debateId, hostEmail, "저는 찬성합니다")

                target.currentStage shouldBe DebateStage.OPENING_CONS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        DebateHistoryRecordedEvent(debateId, DebateStage.OPENING_PROS, DebateUserRole.HOST, "저는 찬성합니다", now),
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
                    setAuditFields(
                        fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                            .set(DebateHistory::id, "0000000000099")
                            .set(DebateHistory::stage, DebateStage.OPENING_PROS)
                            .set(DebateHistory::content, null)
                            .sample(),
                        createdAt = now,
                    )
                }

                service.forceAdvanceIfOverdue(debateId)

                target.currentStage shouldBe DebateStage.OPENING_CONS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        DebateHistoryRecordedEvent(debateId, DebateStage.OPENING_PROS, DebateUserRole.HOST, null, now),
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
    Given("논박 발언을 진행하면") {
        val turns = listOf(
            Triple(DebateStage.REBUTTAL_PROS, DebatePosition.PROS, DebateStage.REBUTTAL_CONS),
            Triple(DebateStage.REBUTTAL_CONS, DebatePosition.CONS, DebateStage.VERDICT_2),
        )
        for ((stage, position, nextStage) in turns) {
            When("$stage 단계에서 내 논박을 제출하면") {
                Then("논박을 기록하고 $nextStage 단계로 전환한다") {
                    val target = debate(
                        stage = stage,
                    )
                    every { userRepository.findByEmail(hostEmail) } returns
                        fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                    every { debateRepository.findWithLockById(debateId) } returns target
                    every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns member(
                        position = position,
                        role = DebateUserRole.HOST,
                    )
                    every { debateHistoryRepository.findByDebateIdAndStage(debateId, stage) } returns null
                    every { debateHistoryRepository.save(any()) } answers {
                        firstArg<DebateHistory>().stage shouldBe stage
                        setAuditFields(
                            entity = fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                                .set(DebateHistory::id, "0000000000099")
                                .set(DebateHistory::stage, stage)
                                .set(DebateHistory::content, "상대방의 주장에 반박합니다")
                                .sample(),
                            createdAt = now,
                        )
                    }

                    service.submit(
                        debateId = debateId,
                        userEmail = hostEmail,
                        content = "상대방의 주장에 반박합니다",
                    )

                    target.currentStage shouldBe nextStage
                    verify(exactly = 1) {
                        eventPublisher.publishEvent(match<DebateHistoryRecordedEvent> {
                            it.stage == stage && it.content == "상대방의 주장에 반박합니다"
                        })
                        eventPublisher.publishEvent(match<DebateStageChangedEvent> { it.stage == nextStage })
                    }
                }
            }

            When("$stage 단계에서 상대방이 제출하면") {
                Then("턴 외 제출을 거부한다") {
                    every { userRepository.findByEmail(hostEmail) } returns
                        fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                    every { debateRepository.findWithLockById(debateId) } returns debate(
                        stage = stage,
                    )
                    every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns member(
                        position = if (position == DebatePosition.PROS) DebatePosition.CONS else DebatePosition.PROS,
                        role = DebateUserRole.HOST,
                    )

                    shouldThrow<DebateNotYourTurnException> {
                        service.submit(
                            debateId = debateId,
                            userEmail = hostEmail,
                            content = "턴 외 논박",
                        )
                    }
                    verify(exactly = 0) { debateHistoryRepository.save(any()) }
                }
            }

            When("$stage 단계의 논박이 이미 기록되어 있으면") {
                Then("중복 제출을 거부한다") {
                    every { userRepository.findByEmail(hostEmail) } returns
                        fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                    every { debateRepository.findWithLockById(debateId) } returns debate(
                        stage = stage,
                    )
                    every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns member(
                        position = position,
                        role = DebateUserRole.HOST,
                    )
                    every { debateHistoryRepository.findByDebateIdAndStage(debateId, stage) } returns
                        fixtureMonkey.giveMeKotlinBuilder<DebateHistory>().sample()

                    shouldThrow<DebateHistoryAlreadySubmittedException> {
                        service.submit(
                            debateId = debateId,
                            userEmail = hostEmail,
                            content = "중복 논박",
                        )
                    }
                    verify(exactly = 0) { debateHistoryRepository.save(any()) }
                }
            }

            When("$stage 단계의 제한 시간이 지나면") {
                Then("무응답을 기록하고 $nextStage 단계로 전환한다") {
                    val target = debate(
                        stage = stage,
                        currentStageStartedAt = now.minusSeconds(120),
                        turnTimeSeconds = 60,
                    )
                    every { debateRepository.findWithLockById(debateId) } returns target
                    every { debateHistoryRepository.findByDebateIdAndStage(debateId, stage) } returns null
                    every { debateUserRepository.findByDebateIdAndPosition(debateId, position) } returns member(
                        position = position,
                        role = DebateUserRole.HOST,
                    )
                    every { debateHistoryRepository.save(any()) } answers {
                        firstArg<DebateHistory>().content shouldBe null
                        setAuditFields(
                            entity = fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                                .set(DebateHistory::id, "0000000000099")
                                .set(DebateHistory::stage, stage)
                                .set(DebateHistory::content, null)
                                .sample(),
                            createdAt = now,
                        )
                    }

                    service.forceAdvanceIfOverdue(
                        debateId = debateId,
                    )

                    target.currentStage shouldBe nextStage
                    verify(exactly = 1) {
                        eventPublisher.publishEvent(match<DebateHistoryRecordedEvent> {
                            it.stage == stage && it.content == null
                        })
                        eventPublisher.publishEvent(match<DebateStageChangedEvent> { it.stage == nextStage })
                    }
                }
            }
        }
    }
    Given("토론이 종료된 후 발언 기록을 조회하면") {
        Then("참여자에게 저장된 최종 판결의 점수와 근거를 반환한다") {
            val target = debate(
                stage = DebateStage.FINAL_VERDICT,
            )
            target.finish(
                winnerPosition = WinnerPosition.PROS,
            )
            val finalVerdict = setAuditFields(
                entity = fixtureMonkey.giveMeKotlinBuilder<DebateVerdict>()
                    .set(DebateVerdict::id, "0000000000099")
                    .set(DebateVerdict::stage, DebateStage.FINAL_VERDICT)
                    .set(DebateVerdict::status, DebateVerdictStatus.SUCCESS)
                    .set(DebateVerdict::winnerPosition, WinnerPosition.PROS)
                    .set(DebateVerdict::reasoning, "전체 발언을 종합한 판결 근거")
                    .set(DebateVerdict::participantProsScore, BigDecimal("62.5"))
                    .set(DebateVerdict::participantConsScore, BigDecimal("37.5"))
                    .sample(),
                createdAt = now,
            )
            every { userRepository.findByEmail(hostEmail) } returns
                fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
            every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns member(
                position = DebatePosition.PROS,
                role = DebateUserRole.HOST,
            )
            every { debateRepository.findWithLockById(debateId) } returns target
            every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns emptyList()
            every { debateVerdictRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns listOf(finalVerdict)

            val response = service.getHistories(
                debateId = debateId,
                userEmail = hostEmail,
            )

            response.verdicts.single().winnerPosition shouldBe WinnerPosition.PROS
            response.verdicts.single().participantProsScore shouldBe BigDecimal("62.5")
            response.verdicts.single().participantConsScore shouldBe BigDecimal("37.5")
            response.verdicts.single().reasoning shouldBe "전체 발언을 종합한 판결 근거"
            verify(exactly = 0) { debateHistoryRepository.save(any()) }
        }
    }
})
