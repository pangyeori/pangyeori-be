package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.domain.DebateVerdict
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.event.DebateVerdictIssuedEvent
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DebateVerdictRecordService(
    private val debateRepository: DebateRepository,
    private val debateVerdictRepository: DebateVerdictRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun record(
        debateId: String,
        stage: DebateStage,
        judgment: DebateVerdictJudgment?,
    ) {
        val debate = debateRepository.findWithLockById(id = debateId) ?: return
        if (debate.currentStage != stage) return
        if (debateVerdictRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) return

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
                turnEndsAt = debate.currentStageEndsAt(),
            ),
        )

        logger.info { "AI 중간 판결을 발급했습니다. debateId=$debateId, stage=$stage, status=${verdict.status}" }
    }

    private fun nextStageAfterVerdict(
        stage: DebateStage,
    ) = when (stage) {
        DebateStage.VERDICT_1 -> DebateStage.REBUTTAL_PROS
        DebateStage.VERDICT_2 -> DebateStage.FREE
        else -> throw IllegalStateException("이 판정 단계 다음 단계가 아직 정의되지 않았습니다: $stage")
    }
}
