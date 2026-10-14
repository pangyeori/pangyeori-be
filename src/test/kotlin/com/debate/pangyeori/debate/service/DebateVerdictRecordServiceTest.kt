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
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
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
})
