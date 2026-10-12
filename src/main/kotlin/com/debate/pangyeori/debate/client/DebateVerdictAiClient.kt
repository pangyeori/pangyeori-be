package com.debate.pangyeori.debate.client

import com.debate.pangyeori.debate.domain.enums.WinnerPosition

interface DebateVerdictAiClient {
    fun judge(
        request: DebateVerdictJudgmentRequest,
    ): DebateVerdictJudgment

    data class DebateVerdictJudgmentRequest(
        val title: String,
        val description: String?,
        val prosStatement: String?,
        val consStatement: String?,
    )

    data class DebateVerdictJudgment(
        val winnerPosition: WinnerPosition,
        val reasoning: String,
    )
}
