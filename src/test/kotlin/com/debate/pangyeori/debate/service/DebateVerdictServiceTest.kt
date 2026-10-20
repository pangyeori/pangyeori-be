package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.repository.DebateFreeMessageRepository
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
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
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class DebateVerdictServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateHistoryRepository = mockk<DebateHistoryRepository>()
    val debateVerdictRepository = mockk<DebateVerdictRepository>()
    val debateVerdictAiClient = mockk<DebateVerdictAiClient>()
    val debateVerdictRecordService = mockk<DebateVerdictRecordService>(relaxed = true)
    val debateFreeMessageRepository = mockk<DebateFreeMessageRepository>()
    val service = DebateVerdictService(
        debateRepository = debateRepository,
        debateHistoryRepository = debateHistoryRepository,
        debateVerdictRepository = debateVerdictRepository,
        debateVerdictAiClient = debateVerdictAiClient,
        debateVerdictRecordService = debateVerdictRecordService,
        debateFreeMessageRepository = debateFreeMessageRepository,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val now = LocalDateTime.now(ZoneOffset.UTC)

    beforeEach {
        clearMocks(debateFreeMessageRepository)
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
    Given("최종 판결을 발급하면") {
        listOf(DebatePosition.PROS, DebatePosition.CONS).forEach { hostPosition ->
            Then("방장이 ${hostPosition.code}일 때 전체 발언과 자유 토론의 포지션 및 순서를 전달한다") {
                val target = debate(stage = DebateStage.FINAL_VERDICT)
                val finalDebate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, debateId)
                    .set(Debate::status, DebateStatus.IN_PROGRESS)
                    .set(Debate::currentStage, DebateStage.FINAL_VERDICT)
                    .set(Debate::title, target.title)
                    .set(Debate::description, null)
                    .set(Debate::hostPosition, hostPosition)
                    .sample()
                val rebuttals = listOf(DebateStage.REBUTTAL_PROS, DebateStage.REBUTTAL_CONS).map { stage ->
                    fixtureMonkey.giveMeKotlinBuilder<DebateHistory>()
                        .set(DebateHistory::stage, stage)
                        .set(DebateHistory::content, stage.code)
                        .sample()
                }
                val messages = listOf(DebateUserRole.GUEST, DebateUserRole.HOST, DebateUserRole.GUEST).map { role ->
                    fixtureMonkey.giveMeKotlinBuilder<DebateFreeMessage>()
                        .set(DebateFreeMessage::role, role)
                        .set(DebateFreeMessage::content, "${role.code}의 자유 발언")
                        .sample()
                }
                every { debateRepository.findById(debateId) } returns Optional.of(finalDebate)
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories() + rebuttals
                every { debateFreeMessageRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(debateId) } returns messages
                val request = slot<DebateVerdictJudgmentRequest>()
                every { debateVerdictAiClient.judge(capture(request)) } returns DebateVerdictJudgment(
                    winnerPosition = WinnerPosition.CONS,
                    reasoning = "전체 발언에서 찬성 측이 더 구체적으로 대응했습니다.",
                    participantProsScore = BigDecimal("62.5"),
                    participantConsScore = BigDecimal("37.5"),
                )

                service.issueVerdict(
                    debateId = debateId,
                    stage = DebateStage.FINAL_VERDICT,
                )

                request.captured.prosStatement shouldBe "저는 찬성합니다"
                request.captured.consStatement shouldBe "저는 반대합니다"
                request.captured.prosRebuttal shouldBe DebateStage.REBUTTAL_PROS.code
                request.captured.consRebuttal shouldBe DebateStage.REBUTTAL_CONS.code
                request.captured.freeMessages.map { it.position } shouldBe
                    listOf(hostPosition.opposite(), hostPosition, hostPosition.opposite())
                request.captured.freeMessages.map { it.content } shouldBe messages.map { it.content }
                verify(exactly = 1) {
                    debateVerdictRecordService.record(
                        debateId = debateId,
                        stage = DebateStage.FINAL_VERDICT,
                        judgment = match {
                            it.winnerPosition == WinnerPosition.PROS && it.participantProsScore == BigDecimal("62.5")
                        },
                    )
                }
            }
        }

        listOf(
            Triple("50", "50.0", WinnerPosition.DRAW),
            Triple("0", "100", WinnerPosition.CONS),
            Triple("100.0", "0.0", WinnerPosition.PROS),
        ).forEach { (pros, cons, winner) ->
            Then("점수가 $pros 대 $cons 이면 서버가 $winner 판정을 결정한다") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate(stage = DebateStage.FINAL_VERDICT))
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns emptyList()
                every { debateFreeMessageRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(debateId) } returns emptyList()
                every { debateVerdictAiClient.judge(any()) } returns DebateVerdictJudgment(
                    winnerPosition = WinnerPosition.DRAW,
                    reasoning = "종합 평가 결과입니다.",
                    participantProsScore = BigDecimal(pros),
                    participantConsScore = BigDecimal(cons),
                )

                service.issueVerdict(
                    debateId = debateId,
                    stage = DebateStage.FINAL_VERDICT,
                )

                verify(exactly = 1) {
                    debateVerdictRecordService.record(debateId, DebateStage.FINAL_VERDICT, match { it.winnerPosition == winner })
                }
            }
        }

        listOf(
            Triple(null, "50", "점수 누락"),
            Triple("-1", "101", "음수와 상한 초과"),
            Triple("60", "50", "합계 오류"),
            Triple("50.01", "49.99", "소수점 자릿수 오류"),
            Triple("50", "50", ""),
        ).forEach { (pros, cons, reasoning) ->
            Then("최종 판결 응답이 유효하지 않으면 실패 기록을 위임한다: $pros / $cons / $reasoning") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate(stage = DebateStage.FINAL_VERDICT))
                every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
                every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns emptyList()
                every { debateFreeMessageRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(debateId) } returns emptyList()
                every { debateVerdictAiClient.judge(any()) } returns DebateVerdictJudgment(
                    winnerPosition = WinnerPosition.PROS,
                    reasoning = reasoning,
                    participantProsScore = pros?.let { BigDecimal(it) },
                    participantConsScore = BigDecimal(cons),
                )

                service.issueVerdict(
                    debateId = debateId,
                    stage = DebateStage.FINAL_VERDICT,
                )

                verify(exactly = 1) { debateVerdictRecordService.record(debateId, DebateStage.FINAL_VERDICT, null) }
            }
        }

        Then("AI 호출 실패 시 실패 기록을 위임한다") {
            every { debateRepository.findById(debateId) } returns Optional.of(debate(stage = DebateStage.FINAL_VERDICT))
            every { debateVerdictRepository.findByDebateIdAndStage(debateId, DebateStage.FINAL_VERDICT) } returns null
            every { debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId) } returns histories()
            every { debateFreeMessageRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(debateId) } returns emptyList()
            every { debateVerdictAiClient.judge(any()) } throws IllegalStateException("timeout")

            service.issueVerdict(
                debateId = debateId,
                stage = DebateStage.FINAL_VERDICT,
            )

            verify(exactly = 1) { debateVerdictRecordService.record(debateId, DebateStage.FINAL_VERDICT, null) }
        }

        Then("종료된 토론에는 AI를 호출하지 않는다") {
            val target = debate(stage = DebateStage.FINAL_VERDICT)
            target.finish(
                winnerPosition = WinnerPosition.PROS,
            )
            every { debateRepository.findById(debateId) } returns Optional.of(target)

            service.issueVerdict(
                debateId = debateId,
                stage = DebateStage.FINAL_VERDICT,
            )

            verify(exactly = 0) { debateVerdictAiClient.judge(any()) }
        }
    }
})
