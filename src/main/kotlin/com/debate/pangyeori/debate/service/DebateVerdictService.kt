package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.event.DebateVerdictIssuedEvent
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DebateVerdictService(
    private val debateRepository: DebateRepository,
    private val debateHistoryRepository: DebateHistoryRepository,
    private val debateVerdictRepository: DebateVerdictRepository,
    private val debateVerdictAiClient: DebateVerdictAiClient,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun issueVerdict(
        debateId: String,
        stage: DebateStage,
    ) {
        val debate = debateRepository.findWithLockById(id = debateId) ?: return
        if (debate.currentStage != stage) return
        if (debateVerdictRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) return

        val histories = debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId = debateId)
        val judgment = requestJudgment(debate = debate, histories = histories, stage = stage)

        val verdict = debateVerdictRepository.save(
            if (judgment != null) {
                DebateVerdict.success(
                    debate = debate,
                    stage = stage,
                    winnerPosition = judgment.winnerPosition,
                    reasoning = judgment.reasoning,
                )
            } else {
                DebateVerdict.failure(
                    debate = debate,
                    stage = stage,
                )
            },
        )

        eventPublisher.publishEvent(
            DebateVerdictIssuedEvent(
                debateId = debate.id!!,
                stage = verdict.stage,
                status = verdict.status,
                winnerPosition = verdict.winnerPosition,
                reasoning = verdict.reasoning,
                createdAt = verdict.createdAt!!,
            ),
        )

        val nextStage = nextStageAfterVerdict(stage = stage)
        debate.advanceStage(next = nextStage)

        eventPublisher.publishEvent(
            DebateStageChangedEvent(
                debateId = debate.id!!,
                stage = nextStage,
                turnEndsAt = debate.currentTurnEndsAt(),
            ),
        )

        logger.info { "AI 중간 판결을 발급했습니다. debateId=$debateId, stage=$stage, status=${verdict.status}" }
    }

    private fun requestJudgment(
        debate: Debate,
        histories: List<DebateHistory>,
        stage: DebateStage,
    ): DebateVerdictJudgment? = runCatching {
        debateVerdictAiClient.judge(
            request = DebateVerdictJudgmentRequest(
                title = debate.title,
                description = debate.description,
                stage = stage,
                prosStatement = statementFor(histories = histories, stage = DebateStage.OPENING_PROS),
                consStatement = statementFor(histories = histories, stage = DebateStage.OPENING_CONS),
                prosRebuttal = statementFor(histories = histories, stage = DebateStage.REBUTTAL_PROS),
                consRebuttal = statementFor(histories = histories, stage = DebateStage.REBUTTAL_CONS),
            ),
        )
    }.onFailure {
        logger.warn(it) { "AI 중간 판결 호출에 실패했습니다. debateId=${debate.id}" }
    }.getOrNull()

    private fun statementFor(
        histories: List<DebateHistory>,
        stage: DebateStage,
    ) = histories.firstOrNull { it.stage == stage }?.content

    private fun nextStageAfterVerdict(
        stage: DebateStage,
    ) = when (stage) {
        DebateStage.VERDICT_1 -> DebateStage.REBUTTAL_PROS
        DebateStage.VERDICT_2 -> DebateStage.FREE
        else -> throw IllegalStateException("이 판정 단계 다음 단계가 아직 정의되지 않았습니다: $stage")
    }
}
