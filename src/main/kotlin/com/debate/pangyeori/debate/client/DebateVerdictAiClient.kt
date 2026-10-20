package com.debate.pangyeori.debate.client

import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import java.math.BigDecimal

interface DebateVerdictAiClient {
    fun judge(
        request: DebateVerdictJudgmentRequest,
    ): DebateVerdictJudgment

    data class DebateVerdictJudgmentRequest(
        val title: String,
        val description: String?,
        val stage: DebateStage,
        val prosStatement: String?,
        val consStatement: String?,
        val prosRebuttal: String?,
        val consRebuttal: String?,
        val freeMessages: List<FreeMessage> = emptyList(),
    )

    data class FreeMessage(
        val position: DebatePosition,
        val content: String,
    )

    data class DebateVerdictJudgment(
        val winnerPosition: WinnerPosition,
        val reasoning: String,
        val participantProsScore: BigDecimal? = null,
        val participantConsScore: BigDecimal? = null,
    )
}
