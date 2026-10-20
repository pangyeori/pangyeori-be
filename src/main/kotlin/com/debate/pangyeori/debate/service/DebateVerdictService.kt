package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.FreeMessage
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.exception.DebateVerdictInvalidException
import com.debate.pangyeori.debate.repository.DebateFreeMessageRepository
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class DebateVerdictService(
    private val debateRepository: DebateRepository,
    private val debateHistoryRepository: DebateHistoryRepository,
    private val debateVerdictRepository: DebateVerdictRepository,
    private val debateVerdictAiClient: DebateVerdictAiClient,
    private val debateVerdictRecordService: DebateVerdictRecordService,
    private val debateFreeMessageRepository: DebateFreeMessageRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun issueVerdict(
        debateId: String,
        stage: DebateStage,
    ) {
        val debate = debateRepository.findById(debateId).orElse(null) ?: return
        if (debate.status != DebateStatus.IN_PROGRESS) return
        if (stage != DebateStage.VERDICT_1 && stage != DebateStage.VERDICT_2 && stage != DebateStage.FINAL_VERDICT) return
        if (debate.currentStage != stage) return
        if (debateVerdictRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) return

        val histories = debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId = debateId)
        val freeMessages = if (stage == DebateStage.FINAL_VERDICT) {
            debateFreeMessageRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(
                debateId = debateId,
            ).map {
                FreeMessage(
                    position = if (it.role == DebateUserRole.HOST) debate.hostPosition else debate.hostPosition.opposite(),
                    content = it.content,
                )
            }
        } else {
            emptyList()
        }
        val judgment = requestJudgment(
            debate = debate,
            histories = histories,
            stage = stage,
            freeMessages = freeMessages,
        )

        debateVerdictRecordService.record(
            debateId = debateId,
            stage = stage,
            judgment = judgment,
        )
    }

    private fun requestJudgment(
        debate: Debate,
        histories: List<DebateHistory>,
        stage: DebateStage,
        freeMessages: List<FreeMessage>,
    ): DebateVerdictJudgment? = runCatching {
        val judgment = debateVerdictAiClient.judge(
            request = DebateVerdictJudgmentRequest(
                title = debate.title,
                description = debate.description,
                stage = stage,
                prosStatement = statementFor(histories = histories, stage = DebateStage.OPENING_PROS),
                consStatement = statementFor(histories = histories, stage = DebateStage.OPENING_CONS),
                prosRebuttal = statementFor(histories = histories, stage = DebateStage.REBUTTAL_PROS),
                consRebuttal = statementFor(histories = histories, stage = DebateStage.REBUTTAL_CONS),
                freeMessages = freeMessages,
            ),
        )

        if (stage == DebateStage.FINAL_VERDICT) validateFinalJudgment(judgment = judgment) else judgment
    }.onFailure {
        logger.error {
            "AI 판결 호출 또는 응답 검증에 실패했습니다. debateId=${debate.id}, stage=$stage, errorType=${it.javaClass.simpleName}"
        }
    }.getOrNull()

    private fun validateFinalJudgment(
        judgment: DebateVerdictJudgment,
    ): DebateVerdictJudgment {
        val pros = judgment.participantProsScore ?: throw DebateVerdictInvalidException()
        val cons = judgment.participantConsScore ?: throw DebateVerdictInvalidException()
        if (judgment.reasoning.isBlank() || pros < BigDecimal.ZERO || cons < BigDecimal.ZERO ||
            pros > TOTAL_SCORE || cons > TOTAL_SCORE || (pros + cons).compareTo(TOTAL_SCORE) != 0 ||
            pros.stripTrailingZeros().scale() > 1 || cons.stripTrailingZeros().scale() > 1
        ) {
            throw DebateVerdictInvalidException()
        }

        return judgment.copy(
            winnerPosition = when {
                pros > cons -> WinnerPosition.PROS
                cons > pros -> WinnerPosition.CONS
                else -> WinnerPosition.DRAW
            },
        )
    }

    private fun statementFor(
        histories: List<DebateHistory>,
        stage: DebateStage,
    ) = histories.firstOrNull { it.stage == stage }?.content

    companion object {
        private val TOTAL_SCORE = BigDecimal("100")
    }
}
