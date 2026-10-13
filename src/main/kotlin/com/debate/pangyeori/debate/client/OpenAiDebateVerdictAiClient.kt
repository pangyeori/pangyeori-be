package com.debate.pangyeori.debate.client

import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgment
import com.debate.pangyeori.debate.client.DebateVerdictAiClient.DebateVerdictJudgmentRequest
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class OpenAiDebateVerdictAiClient(
    private val chatClient: ChatClient,
) : DebateVerdictAiClient {

    override fun judge(
        request: DebateVerdictJudgmentRequest,
    ): DebateVerdictJudgment {
        val response = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(buildUserPrompt(request = request))
            .call()
            .entity(AiVerdictResponse::class.java)
            ?: throw IllegalStateException("AI 판정 응답이 비어있습니다")

        return DebateVerdictJudgment(
            winnerPosition = response.winnerPosition,
            reasoning = response.reasoning,
        )
    }

    private fun buildUserPrompt(
        request: DebateVerdictJudgmentRequest,
    ): String {
        if (request.stage == DebateStage.VERDICT_2) {
            return """
                토론 주제: ${request.title}
                토론 설명: ${request.description ?: "없음"}

                찬성(PROS) 측 입장 발언: ${request.prosStatement ?: "시간 초과로 제출하지 않음"}
                반대(CONS) 측 입장 발언: ${request.consStatement ?: "시간 초과로 제출하지 않음"}

                찬성(PROS) 측 논박 발언: ${request.prosRebuttal ?: "시간 초과로 제출하지 않음"}
                반대(CONS) 측 논박 발언: ${request.consRebuttal ?: "시간 초과로 제출하지 않음"}

                입장 발언은 맥락으로 참고하고, 위 논박 발언을 기준으로 어느 쪽이 상대 주장에 더 타당하게 대응했는지 평가해주세요.
                아직 토론이 끝나지 않았으므로 최종 승패를 확정하지 말고, 논박 구간에 대한 중간 평가로만 답해주세요.
            """.trimIndent()
        }

        return """
            토론 주제: ${request.title}
            토론 설명: ${request.description ?: "없음"}

            찬성(PROS) 측 입장 발언: ${request.prosStatement ?: "시간 초과로 제출하지 않음"}
            반대(CONS) 측 입장 발언: ${request.consStatement ?: "시간 초과로 제출하지 않음"}

            위 입장 발언만을 근거로 현재 시점에서 어느 쪽 주장이 더 설득력 있는지 평가해주세요.
            아직 토론이 끝나지 않았으므로 최종 승패를 확정하지 말고, 지금까지의 발언에 대한 중간 평가로만 답해주세요.
        """.trimIndent()
    }

    private data class AiVerdictResponse(
        val winnerPosition: WinnerPosition,
        val reasoning: String,
    )

    companion object {
        private const val SYSTEM_PROMPT = "당신은 1대1 직파토론을 중간에 평가하는 AI 심판입니다. 중립적인 태도로 간결하게 평가하세요."
    }
}
