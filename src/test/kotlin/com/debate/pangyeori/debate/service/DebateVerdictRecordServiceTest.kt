package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.event.DebateVerdictIssuedEvent
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

class DebateVerdictRecordServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateVerdictRepository = mockk<DebateVerdictRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = DebateVerdictRecordService(
        debateRepository = debateRepository,
        debateVerdictRepository = debateVerdictRepository,
        eventPublisher = eventPublisher,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val now = LocalDateTime.now(ZoneOffset.UTC)

    beforeEach {
        clearMocks(debateRepository, debateVerdictRepository, eventPublisher)
    }

    fun debate(
        stage: DebateStage,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::status, DebateStatus.IN_PROGRESS)
        .set(Debate::currentStage, stage)
        .set(Debate::currentStageStartedAt, now)
        .sample()

    fun verdict(
        status: DebateVerdictStatus,
        stage: DebateStage,
        winnerPosition: WinnerPosition? = null,
        reasoning: String? = null,
    ) = setAuditFields(
        fixtureMonkey.giveMeKotlinBuilder<DebateVerdict>()
            .set(DebateVerdict::id, "0000000000099")
            .set(DebateVerdict::stage, stage)
            .set(DebateVerdict::status, status)
            .set(DebateVerdict::winnerPosition, winnerPosition)
            .set(DebateVerdict::reasoning, reasoning)
            .sample(),
        createdAt = now,
    )

    Given("1차 판결을 기록하면") {
        When("판정에 성공했으면") {
            Then("판결을 저장하고 반박 발언 단계로 전환한다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateVerdictRepository.save(any()) } answers {
                    verdict(
                        status = DebateVerdictStatus.SUCCESS,
                        stage = DebateStage.VERDICT_1,
                        winnerPosition = WinnerPosition.PROS,
                        reasoning = "찬성 측이 더 설득력 있습니다",
                    )
                }

                service.record(
                    debateId = debateId,
                    stage = DebateStage.VERDICT_1,
                    judgment = DebateVerdictJudgment(winnerPosition = WinnerPosition.PROS, reasoning = "찬성 측이 더 설득력 있습니다"),
                )

                target.currentStage shouldBe DebateStage.REBUTTAL_PROS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateVerdictIssuedEvent> {
                            it.status == DebateVerdictStatus.SUCCESS && it.winnerPosition == WinnerPosition.PROS
                        },
                    )
                }
                verify(exactly = 1) {
                    eventPublisher.publishEvent(match<DebateStageChangedEvent> { it.stage == DebateStage.REBUTTAL_PROS })
                }
            }
        }

        When("판정이 null(AI 호출 실패)이면") {
            Then("실패 상태로 저장하고도 다음 단계로 넘긴다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateVerdictRepository.save(any()) } answers {
                    verdict(status = DebateVerdictStatus.FAILED, stage = DebateStage.VERDICT_1)
                }

                service.record(debateId = debateId, stage = DebateStage.VERDICT_1, judgment = null)

                target.currentStage shouldBe DebateStage.REBUTTAL_PROS
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateVerdictIssuedEvent> {
                            it.status == DebateVerdictStatus.FAILED && it.winnerPosition == null && it.reasoning == null
                        },
                    )
                }
            }
        }

        When("해당 단계의 판결이 이미 존재하면") {
            Then("다시 저장하지 않는다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns
                    verdict(status = DebateVerdictStatus.SUCCESS, stage = DebateStage.VERDICT_1)

                service.record(
                    debateId = debateId,
                    stage = DebateStage.VERDICT_1,
                    judgment = DebateVerdictJudgment(winnerPosition = WinnerPosition.PROS, reasoning = "이미 발급된 판결"),
                )

                target.currentStage shouldBe DebateStage.VERDICT_1
                verify(exactly = 0) { debateVerdictRepository.save(any()) }
            }
        }

        When("토론이 이미 다른 단계로 넘어갔으면") {
            Then("다시 저장하지 않는다") {
                val target = debate(stage = DebateStage.REBUTTAL_PROS)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.record(
                    debateId = debateId,
                    stage = DebateStage.VERDICT_1,
                    judgment = DebateVerdictJudgment(winnerPosition = WinnerPosition.PROS, reasoning = "x"),
                )

                verify(exactly = 0) { debateVerdictRepository.save(any()) }
            }
        }
    }

    Given("2차 판결을 기록하면") {
        Then("판결을 저장하고 자유 토론 단계로 전환한다") {
            val target = debate(stage = DebateStage.VERDICT_2)
            every { debateRepository.findWithLockById(debateId) } returns target
            every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_2) } returns null
            every { debateVerdictRepository.save(any()) } answers {
                verdict(
                    status = DebateVerdictStatus.SUCCESS,
                    stage = DebateStage.VERDICT_2,
                    winnerPosition = WinnerPosition.CONS,
                    reasoning = "반대측 논박이 더 타당합니다",
                )
            }

            service.record(
                debateId = debateId,
                stage = DebateStage.VERDICT_2,
                judgment = DebateVerdictJudgment(winnerPosition = WinnerPosition.CONS, reasoning = "반대측 논박이 더 타당합니다"),
            )

            target.currentStage shouldBe DebateStage.FREE
            verify(exactly = 1) {
                eventPublisher.publishEvent(match<DebateStageChangedEvent> { it.stage == DebateStage.FREE })
            }
        }
    }
    Given("최종 판결을 기록하면") {
        listOf(WinnerPosition.PROS, WinnerPosition.CONS, WinnerPosition.DRAW).forEach { winner ->
            Then("$winner 판결과 점수를 저장하고 토론을 종료한다") {
                val target = debate(stage = DebateStage.FINAL_VERDICT)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
                val saved = slot<DebateVerdict>()
                every { debateVerdictRepository.save(capture(saved)) } answers {
                    setAuditFields(
                        entity = firstArg<DebateVerdict>(),
                        createdAt = now,
                    )
                }
                val pros = when {
                    winner == WinnerPosition.PROS -> BigDecimal("60.0")
                    winner == WinnerPosition.CONS -> BigDecimal("40.0")
                    else -> BigDecimal("50.0")
                }
                val cons = BigDecimal("100.0") - pros

                service.record(
                    debateId = debateId,
                    stage = DebateStage.FINAL_VERDICT,
                    judgment = DebateVerdictJudgment(
                        winnerPosition = winner,
                        reasoning = "전체 토론 종합 판결",
                        participantProsScore = pros,
                        participantConsScore = cons,
                    ),
                )

                saved.captured.stage shouldBe DebateStage.FINAL_VERDICT
                saved.captured.participantProsScore shouldBe pros
                saved.captured.participantConsScore shouldBe cons
                saved.captured.reasoning shouldBe "전체 토론 종합 판결"
                target.status shouldBe DebateStatus.FINISHED
                target.winnerPosition shouldBe winner
                target.currentStage shouldBe DebateStage.FINAL_VERDICT
                target.currentStageEndsAt() shouldBe null
                verify(exactly = 1) {
                    eventPublisher.publishEvent(
                        match<DebateVerdictIssuedEvent> {
                            it.stage == DebateStage.FINAL_VERDICT && it.winnerPosition == winner &&
                                it.participantProsScore == pros && it.participantConsScore == cons
                        },
                    )
                }
                verify(exactly = 0) {
                    eventPublisher.publishEvent(match<Any> { it is DebateStageChangedEvent })
                }
            }
        }

        Then("AI 호출에 실패해도 실패 상태를 저장하고 승자 없이 토론을 종료한다") {
            val target = debate(stage = DebateStage.FINAL_VERDICT)
            every { debateRepository.findWithLockById(debateId) } returns target
            every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
            val saved = slot<DebateVerdict>()
            every { debateVerdictRepository.save(capture(saved)) } answers {
                setAuditFields(
                    entity = firstArg<DebateVerdict>(),
                    createdAt = now,
                )
            }

            service.record(
                debateId = debateId,
                stage = DebateStage.FINAL_VERDICT,
                judgment = null,
            )

            saved.captured.status shouldBe DebateVerdictStatus.FAILED
            saved.captured.winnerPosition shouldBe null
            saved.captured.participantProsScore shouldBe null
            saved.captured.participantConsScore shouldBe null
            target.status shouldBe DebateStatus.FINISHED
            target.winnerPosition shouldBe null
            verify(exactly = 1) {
                eventPublisher.publishEvent(
                    match<DebateVerdictIssuedEvent> {
                        it.stage == DebateStage.FINAL_VERDICT && it.status == DebateVerdictStatus.FAILED
                    },
                )
            }
        }

        Then("완료한 최종 판결을 다시 기록하지 않는다") {
            val target = debate(stage = DebateStage.FINAL_VERDICT)
            target.finish(
                winnerPosition = WinnerPosition.CONS,
            )
            every { debateRepository.findWithLockById(debateId) } returns target

            service.record(
                debateId = debateId,
                stage = DebateStage.FINAL_VERDICT,
                judgment = null,
            )

            target.winnerPosition shouldBe WinnerPosition.CONS
            verify(exactly = 0) { debateVerdictRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any<DebateVerdictIssuedEvent>()) }
        }

        Then("취소된 토론에는 판결을 기록하지 않는다") {
            val target = debate(stage = DebateStage.FINAL_VERDICT)
            target.status = DebateStatus.CANCELLED
            every { debateRepository.findWithLockById(debateId) } returns target

            service.record(
                debateId = debateId,
                stage = DebateStage.FINAL_VERDICT,
                judgment = null,
            )

            target.status shouldBe DebateStatus.CANCELLED
            verify(exactly = 0) { debateVerdictRepository.save(any()) }
        }
    }
})
