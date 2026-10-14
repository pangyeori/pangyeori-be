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
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class DebateVerdictServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateHistoryRepository = mockk<DebateHistoryRepository>()
    val debateVerdictRepository = mockk<DebateVerdictRepository>()
    val debateVerdictAiClient = mockk<DebateVerdictAiClient>()
    val debateVerdictRecordService = mockk<DebateVerdictRecordService>(relaxed = true)
    val service = DebateVerdictService(
        debateRepository = debateRepository,
        debateHistoryRepository = debateHistoryRepository,
        debateVerdictRepository = debateVerdictRepository,
        debateVerdictAiClient = debateVerdictAiClient,
        debateVerdictRecordService = debateVerdictRecordService,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val now = LocalDateTime.now(ZoneOffset.UTC)

    beforeEach {
        clearMocks(debateRepository, debateHistoryRepository, debateVerdictRepository, debateVerdictAiClient, debateVerdictRecordService)
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

    Given("1차 AI 중간 판결을 발급하면") {
        When("AI 호출이 성공하면") {
            Then("판결 기록을 위임한다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findById(debateId) } returns Optional.of(target)
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories()
                every { debateVerdictAiClient.judge(any()) } returns
                    DebateVerdictJudgment(winnerPosition = WinnerPosition.PROS, reasoning = "찬성 측이 더 설득력 있습니다")

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                verify(exactly = 1) {
                    debateVerdictAiClient.judge(
                        DebateVerdictJudgmentRequest(
                            title = "AI 판결 검증 토론",
                            description = null,
                            stage = DebateStage.VERDICT_1,
                            prosStatement = "저는 찬성합니다",
                            consStatement = "저는 반대합니다",
                            prosRebuttal = null,
                            consRebuttal = null,
                        ),
                    )
                }
                verify(exactly = 1) {
                    debateVerdictRecordService.record(
                        debateId = debateId,
                        stage = DebateStage.VERDICT_1,
                        judgment = match { it.winnerPosition == WinnerPosition.PROS },
                    )
                }
            }
        }

        When("AI 호출이 거듭 실패하면") {
            Then("판정 없이(null) 기록을 위임한다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findById(debateId) } returns Optional.of(target)
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories()
                every { debateVerdictAiClient.judge(any()) } throws IllegalStateException("AI 호출 타임아웃")

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                verify(exactly = 1) {
                    debateVerdictRecordService.record(debateId = debateId, stage = DebateStage.VERDICT_1, judgment = null)
                }
            }
        }

        When("해당 단계의 판결이 이미 존재하면") {
            Then("AI를 호출하지 않는다") {
                val target = debate(stage = DebateStage.VERDICT_1)
                every { debateRepository.findById(debateId) } returns Optional.of(target)
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_1) } returns
                    setAuditFields(
                        fixtureMonkey.giveMeKotlinBuilder<DebateVerdict>()
                            .set(DebateVerdict::stage, DebateStage.VERDICT_1)
                            .set(DebateVerdict::status, DebateVerdictStatus.SUCCESS)
                            .sample(),
                        createdAt = now,
                    )

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                verify(exactly = 0) { debateVerdictAiClient.judge(any()) }
                verify(exactly = 0) { debateVerdictRecordService.record(any(), any(), any()) }
            }
        }

        When("토론이 이미 다른 단계로 넘어갔으면") {
            Then("AI를 호출하지 않는다") {
                val target = debate(stage = DebateStage.REBUTTAL_PROS)
                every { debateRepository.findById(debateId) } returns Optional.of(target)

                service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_1)

                verify(exactly = 0) { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(any()) }
                verify(exactly = 0) { debateVerdictRecordService.record(any(), any(), any()) }
            }
        }
    }

    Given("2차 AI 중간 판결을 발급하면") {
        Then("입장 발언과 반박 발언을 모두 AI에 전달한다") {
            val target = debate(stage = DebateStage.VERDICT_2)
            val rebuttals = listOf(
                fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                    .set(DebateHistory::stage, DebateStage.REBUTTAL_PROS)
                    .set(DebateHistory::content, "반대측 근거에 반박합니다")
                    .sample(),
                fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                    .set(DebateHistory::stage, DebateStage.REBUTTAL_CONS)
                    .set(DebateHistory::content, "찬성측 근거에 반박합니다")
                    .sample(),
            )
            every { debateRepository.findById(debateId) } returns Optional.of(target)
            every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.VERDICT_2) } returns null
            every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories() + rebuttals
            every { debateVerdictAiClient.judge(any()) } returns
                DebateVerdictJudgment(winnerPosition = WinnerPosition.CONS, reasoning = "반대측 논박이 더 타당합니다")

            service.issueVerdict(debateId = debateId, stage = DebateStage.VERDICT_2)

            verify(exactly = 1) {
                debateVerdictAiClient.judge(
                    DebateVerdictJudgmentRequest(
                        title = "AI 판결 검증 토론",
                        description = null,
                        stage = DebateStage.VERDICT_2,
                        prosStatement = "저는 찬성합니다",
                        consStatement = "저는 반대합니다",
                        prosRebuttal = "반대측 근거에 반박합니다",
                        consRebuttal = "찬성측 근거에 반박합니다",
                    ),
                )
                debateVerdictRecordService.record(
                    debateId = debateId,
                    stage = DebateStage.VERDICT_2,
                    judgment = match { it.winnerPosition == WinnerPosition.CONS },
                )
            }
        }
    }
})
