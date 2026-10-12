package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.event.DebateVerdictIssuedEvent
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
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

class DebateVerdictServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateHistoryRepository = mockk<DebateHistoryRepository>()
    val debateVerdictRepository = mockk<DebateVerdictRepository>()
    val debateVerdictAiClient = mockk<DebateVerdictAiClient>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = DebateVerdictService(
        debateRepository = debateRepository,
        debateHistoryRepository = debateHistoryRepository,
        debateVerdictRepository = debateVerdictRepository,
        debateVerdictAiClient = debateVerdictAiClient,
        eventPublisher = eventPublisher,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val now = LocalDateTime.now(ZoneOffset.UTC)

    beforeEach {
        clearMocks(debateRepository, debateHistoryRepository, debateVerdictRepository, debateVerdictAiClient, eventPublisher)
    }

    fun debate(
        stage: DebateStage,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::title, "AI 판결 검증 토론")
        .set(Debate::description, null)
        .set(Debate::status, DebateStatus.IN_PROGRESS)
        .set(Debate::currentStage, stage)
        .set(Debate::currentStageStartedAt, now)
        .sample()

    fun histories() = listOf(
        fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
            .set(DebateHistory::stage, DebateStage.OPENING_PROS)
            .set(DebateHistory::content, "저는 찬성합니다")
            .sample(),
        fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
            .set(DebateHistory::stage, DebateStage.OPENING_CONS)
            .set(DebateHistory::content, "저는 반대합니다")
            .sample(),
    )

    fun verdict(
        status: DebateVerdictStatus,
        winnerPosition: WinnerPosition? = null,
        reasoning: String? = null,
    ) = setAuditFields(
        fixtureMonkey.giveMeKotlinBuilder<DebateVerdict>()
            .set(DebateVerdict::id, "0000000000099")
            .set(DebateVerdict::stage, DebateStage.VERDICT_1)
            .set(DebateVerdict::status, status)
            .set(DebateVerdict::winnerPosition, winnerPosition)
            .set(DebateVerdict::reasoning, reasoning)
            .sample(),
        createdAt = now,
    )

    Given("AI 중간 판결을 발급하면") {
        When("AI 호출이 성공하면") {
            Then("판결을 저장하고 다음 단계로 넘기는 이벤트를 발행한다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories()
                every { debateVerdictAiClient.judge(any()) } returns
                    DebateVerdictJudgment(winnerPosition = WinnerPosition.PROS, reasoning = "찬성 측이 더 설득력 있습니다")
                every { debateVerdictRepository.save(any()) } answers {
                    verdict(status = DebateVerdictStatus.SUCCESS, winnerPosition = WinnerPosition.PROS, reasoning = "찬성 측이 더 설득력 있습니다")
                }

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                target.currentStage shouldBe DebateStage.REBUTTAL_PROS
                verify(exactly = 1) {
                    debateVerdictAiClient.judge(
                        DebateVerdictJudgmentRequest(
                            title = "AI 판결 검증 토론",
                            description = null,
                            prosStatement = "저는 찬성합니다",
                            consStatement = "저는 반대합니다",
                        ),
                    )
                }
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

        When("AI 호출이 거듭 실패하면") {
            Then("실패 상태로 판결을 저장하고도 다음 단계로 넘긴다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories()
                every { debateVerdictAiClient.judge(any()) } throws IllegalStateException("AI 호출 타임아웃")
                every { debateVerdictRepository.save(any()) } answers {
                    verdict(status = DebateVerdictStatus.FAILED)
                }

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

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
            Then("다시 처리하지 않는다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findWithLockById(debateId) } returns target
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns
                    verdict(status = DebateVerdictStatus.SUCCESS, winnerPosition = WinnerPosition.PROS, reasoning = "이미 발급된 판결")

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                target.currentStage shouldBe DebateStage.VERDICT_1
                verify(exactly = 0) { debateVerdictAiClient.judge(any()) }
                verify(exactly = 0) { debateVerdictRepository.save(any()) }
            }
        }

        When("토론이 이미 다른 단계로 넘어갔으면") {
            Then("다시 처리하지 않는다") {
                val target = debate(stage = DebateStage.REBUTTAL_PROS)
                every { debateRepository.findWithLockById(debateId) } returns target

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                verify(exactly = 0) { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(any()) }
                verify(exactly = 0) { debateVerdictRepository.save(any()) }
            }
        }
    }
})
