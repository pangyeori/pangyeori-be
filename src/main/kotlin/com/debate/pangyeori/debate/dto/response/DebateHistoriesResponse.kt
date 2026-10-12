package com.debate.pangyeori.debate.dto.response

data class DebateHistoriesResponse(
    val histories: List<DebateHistoryResponse>,
    val verdicts: List<DebateVerdictResponse>,
)
