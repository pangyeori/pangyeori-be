package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.client.DebateVerdictAiClient
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateVerdictRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class DebateVerdictService(
    private val debateRepository: DebateRepository,
    private val debateHistoryRepository: DebateHistoryRepository,
    private val debateVerdictRepository: DebateVerdictRepository,
    private val debateVerdictAiClient: DebateVerdictAiClient,
    private val debateVerdictRecordService: DebateVerdictRecordService,
) {
    private val logger = KotlinLogging.logger {}

    fun issueVerdict(
        debateId: String,
        stage: DebateStage,
    ) {
        val debate = debateRepository.findById(debateId).orElse(null) ?: return
        if (debate.currentStage != stage) return
        if (debateVerdictRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) return

        val histories = debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(debateId = debateId)
        val judgment = requestJudgment(debate = debate, histories = histories, stage = stage)

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
}
