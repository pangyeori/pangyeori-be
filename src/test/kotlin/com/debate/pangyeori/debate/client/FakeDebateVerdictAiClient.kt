package com.debate.pangyeori.debate.client

import com.debate.pangyeori.debate.domain.enums.WinnerPosition

class FakeDebateVerdictAiClient : DebateVerdictAiClient {

    override fun judge(
        request: DebateVerdictAiClient.DebateVerdictJudgmentRequest,
    ) = DebateVerdictAiClient.DebateVerdictJudgment(
        winnerPosition = WinnerPosition.PROS,
        reasoning = "찬성 측이 더 구체적인 근거를 제시해 우세합니다.",
    )
}
