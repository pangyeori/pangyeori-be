package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.auth.token.TokenProvider
import com.debate.pangyeori.config.WebSocketConfig
import com.debate.pangyeori.debate.client.TestDebateVerdictAiClientConfig
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateVerdictStatus
import com.debate.pangyeori.debate.domain.enums.WinnerPosition
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.service.DebateFreeMessageService
import com.debate.pangyeori.debate.service.DebateHistoryService
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.support.asyncapi.AsyncApiDocsTest
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import tools.jackson.databind.ObjectMapper
import org.springframework.web.socket.WebSocketHttpHeaders
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Import(TestDebateVerdictAiClientConfig::class)
class DebateStompIntegrationTest : AsyncApiDocsTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var debateRepository: DebateRepository

    @Autowired
    private lateinit var debateUserRepository: DebateUserRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var debateHistoryService: DebateHistoryService

    @Autowired
    private lateinit var debateFreeMessageService: DebateFreeMessageService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val sessions = mutableListOf<StompSession>()

    @Test
    fun `호스트와 게스트가 모두 입장해 토론이 끝까지 진행되면 각 단계의 이벤트를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!
        val hostToken = tokenProvider.issue(host).accessToken
        lateinit var guestSubscriber: Subscriber

        documentStomp("debates/roomEvents", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                subscribeTo = DebateDestination.of("{debateId}"),
                description = "토론방 입장, 이탈, 진행 이벤트. 먼저 STOMP WebSocket으로 연결한 뒤 이 목적지를 구독한다. " +
                    "클라이언트는 이 채널에 메시지를 보내지 않고 구독만 한다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $hostToken")
                pathValue("debateId", debateId)
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "개설자 입장",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.ENTERED
                    it.role shouldBe DebateUserRole.HOST
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "게스트 입장",
            ) {
                trigger { guestSubscriber = connectAsSubscriber(guest, debateId) }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.ENTERED
                    it.role shouldBe DebateUserRole.GUEST
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "토론 시작",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.type shouldBe DebateConnectionEvent.Type.STARTED }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "찬성 입장 발언 단계 시작",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.OPENING_PROS }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "개설자 발언 제출",
            ) {
                trigger {
                    debateHistoryService.submit(
                        debateId = debateId,
                        userEmail = host.email,
                        content = "저는 이 주제에 찬성합니다",
                    )
                }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.STATEMENT_SUBMITTED
                    it.role shouldBe DebateUserRole.HOST
                    it.content shouldBe "저는 이 주제에 찬성합니다"
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "반대 입장 발언 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.OPENING_CONS }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "게스트 발언 제출",
            ) {
                trigger {
                    debateHistoryService.submit(
                        debateId = debateId,
                        userEmail = guest.email,
                        content = "저는 이 주제에 반대합니다",
                    )
                }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.STATEMENT_SUBMITTED
                    it.role shouldBe DebateUserRole.GUEST
                    it.content shouldBe "저는 이 주제에 반대합니다"
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "AI 1차 중간 판결 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.VERDICT_1 }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "AI 1차 중간 판결 완료",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.VERDICT_ISSUED
                    it.verdictStatus shouldBe DebateVerdictStatus.SUCCESS
                    it.winnerPosition shouldBe WinnerPosition.PROS
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "찬성 반박 발언 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.REBUTTAL_PROS }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "찬성 논박 제출",
            ) {
                trigger {
                    debateHistoryService.submit(
                        debateId = debateId,
                        userEmail = host.email,
                        content = "반대측 주장에 반박합니다",
                    )
                }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.STATEMENT_SUBMITTED
                    it.role shouldBe DebateUserRole.HOST
                    it.content shouldBe "반대측 주장에 반박합니다"
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "반대 논박 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.REBUTTAL_CONS }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "반대 논박 제출",
            ) {
                trigger {
                    debateHistoryService.submit(
                        debateId = debateId,
                        userEmail = guest.email,
                        content = "찬성측 주장에 반박합니다",
                    )
                }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.STATEMENT_SUBMITTED
                    it.role shouldBe DebateUserRole.GUEST
                    it.content shouldBe "찬성측 주장에 반박합니다"
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "AI 2차 중간 판결 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.VERDICT_2 }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "AI 2차 중간 판결 완료",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.VERDICT_ISSUED
                    it.stage shouldBe DebateStage.VERDICT_2
                    it.verdictStatus shouldBe DebateVerdictStatus.SUCCESS
                    it.winnerPosition shouldBe WinnerPosition.PROS
                }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "자유 토론 단계로 전환",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify { it.stage shouldBe DebateStage.FREE }
            }
            receive<DebateConnectionEvent>(
                summary = "토론방 입장, 이탈, 진행 상황이 바뀌면 받는 이벤트",
                example = "게스트 이탈",
            ) {
                trigger { guestSubscriber.session.disconnect() }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (입장, 이탈, 시작, 단계 전환, 발언, 판결, 자유 토론 메시지, 종료)")
                field("role", "입장·이탈한 참여자 또는 발언 제출자의 역할. 시작/단계 전환/판결 이벤트에는 없음").optional()
                field("stage", "발언 제출·시간 초과, 단계 전환, 판결 이벤트에서만 쓰는 토론 단계").optional()
                field("content", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                field("turnEndsAt", "단계 전환 이벤트에서만 쓰는 다음 턴 마감 시각").optional()
                field("submittedAt", "발언 제출/시간 초과 이벤트에서만 쓰는 발언 제출 시각").optional()
                field("verdictStatus", "AI 판결 이벤트에서 쓰는 판결 처리 상태 (SUCCESS, FAILED)").optional()
                field("winnerPosition", "AI 판결 이벤트에서 쓰는 우세 포지션. 판결에 실패한 경우 null").optional()
                field("reasoning", "AI 판결 이벤트에서 쓰는 판결 근거. 판결에 실패한 경우 null").optional()
                field("messageId", "자유 토론 메시지 전송 이벤트에서만 쓰는 메시지 ID").optional()
                field("participantProsScore", "최종 판결 찬성 점수").optional()
                field("participantConsScore", "최종 판결 반대 점수").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.LEFT
                    it.role shouldBe DebateUserRole.GUEST
                }
            }
        }

        debateRepository.findById(debateId).get().status shouldBe DebateStatus.IN_PROGRESS
        debateRepository.findById(debateId).get().currentStage shouldBe DebateStage.FREE
        val histories = debateHistoryService.getHistories(
            debateId = debateId,
            userEmail = host.email,
        )
        histories.histories.map { it.stage } shouldBe listOf(
            DebateStage.OPENING_PROS,
            DebateStage.OPENING_CONS,
            DebateStage.REBUTTAL_PROS,
            DebateStage.REBUTTAL_CONS,
        )
        histories.verdicts.map { it.stage } shouldBe listOf(DebateStage.VERDICT_1, DebateStage.VERDICT_2)
    }

    @Test
    fun `유효하지 않은 토큰으로 연결하면 거절된다`() {
        val connected = runCatching {
            WebSocketStompClient(StandardWebSocketClient()).connectAsync(
                "ws://localhost:$port${WebSocketConfig.ENDPOINT}",
                WebSocketHttpHeaders(),
                StompHeaders().apply { add(AUTHORIZATION, "Bearer invalid-token") },
                object : StompSessionHandlerAdapter() {},
            ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrNull()

        (connected == null) shouldBe true
    }

    @Test
    fun `진행 중인 토론에서 끊긴 참여자는 다시 접속해 입장할 수 있다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!

        val hostFirst = connectAsSubscriber(host, debateId)
        val guestSubscriber = connectAsSubscriber(guest, debateId)
        awaitFrame(hostFirst.frames) { it.contains("\"type\":\"STARTED\"") }

        hostFirst.session.disconnect()

        val hostAgain = connectAsSubscriber(host, debateId)
        awaitFrame(hostAgain.frames) { it.contains("\"type\":\"ENTERED\"") && it.contains("\"role\":\"HOST\"") }
        guestSubscriber.session.disconnect()

        debateRepository.findById(debateId).get().status shouldBe DebateStatus.IN_PROGRESS
    }

    @Test
    fun `자유 토론 종료 후 최종 판결과 종료 이벤트를 받고 저장된 결과를 확인한다`() {
        val host = saveUser(
            prefix = "final-host",
        )
        val guest = saveUser(
            prefix = "final-guest",
        )
        val debate = saveReadyDebate(
            host = host,
            guest = guest,
        )
        debate.start()
        debate.advanceStage(
            next = DebateStage.FREE,
        )
        debateRepository.saveAndFlush(debate)
        val debateId = debate.id!!
        val subscriber = connectAsSubscriber(
            user = host,
            debateId = debateId,
        )
        awaitFrame(
            frames = subscriber.frames,
            matches = { it.contains("\"type\":\"ENTERED\"") },
        )

        debateFreeMessageService.submit(
            debateId = debateId,
            userId = host.id!!,
            content = "찬성 측의 마지막 근거입니다.",
        )
        val overdue = debateRepository.findById(debateId).orElseThrow()
        overdue.currentStageStartedAt = LocalDateTime.now(ZoneOffset.UTC)
            .minusSeconds(overdue.freeDebateTimeSeconds.toLong() + 1)
        debateRepository.saveAndFlush(overdue)
        debateFreeMessageService.forceEndIfOverdue(
            debateId = debateId,
        )

        val received = mutableMapOf<DebateConnectionEvent.Type, DebateConnectionEvent>()
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (System.currentTimeMillis() < deadline &&
            !(received.containsKey(DebateConnectionEvent.Type.VERDICT_ISSUED) &&
                received.containsKey(DebateConnectionEvent.Type.DEBATE_ENDED))
        ) {
            val frame = subscriber.frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val event = objectMapper.readValue(frame, DebateConnectionEvent::class.java)
            received[event.type] = event
        }

        val verdict = received[DebateConnectionEvent.Type.VERDICT_ISSUED]!!
        verdict.stage shouldBe DebateStage.FINAL_VERDICT
        verdict.verdictStatus shouldBe DebateVerdictStatus.SUCCESS
        verdict.winnerPosition shouldBe WinnerPosition.PROS
        verdict.participantProsScore shouldBe BigDecimal("62.5")
        verdict.participantConsScore shouldBe BigDecimal("37.5")
        received[DebateConnectionEvent.Type.DEBATE_ENDED]!!.winnerPosition shouldBe WinnerPosition.PROS
        val finished = debateRepository.findById(debateId).orElseThrow()
        finished.status shouldBe DebateStatus.FINISHED
        finished.winnerPosition shouldBe WinnerPosition.PROS
        finished.currentStageEndsAt() shouldBe null
    }

    private fun connectAsSubscriber(
        user: User,
        debateId: String,
    ): Subscriber {
        val token = tokenProvider.issue(user).accessToken
        val session = WebSocketStompClient(StandardWebSocketClient()).connectAsync(
            "ws://localhost:$port${WebSocketConfig.ENDPOINT}",
            WebSocketHttpHeaders(),
            StompHeaders().apply { add(AUTHORIZATION, "Bearer $token") },
            object : StompSessionHandlerAdapter() {},
        ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        sessions.add(session)
        val frames = LinkedBlockingQueue<String>()
        session.subscribe(
            DebateDestination.of(debateId),
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    (payload as? ByteArray)?.let { frames.add(String(it, Charsets.UTF_8)) }
                }
            },
        )
        return Subscriber(session, frames)
    }

    private fun awaitFrame(
        frames: BlockingQueue<String>,
        matches: (String) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            val frame = frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (matches(frame)) return
        }
        throw AssertionError("조건에 맞는 STOMP 프레임을 ${TIMEOUT_SECONDS}초 안에 받지 못했습니다")
    }

    private class Subscriber(
        val session: StompSession,
        val frames: BlockingQueue<String>,
    )

    private fun saveUser(
        prefix: String,
    ): User = userRepository.save(
        User.create(
            email = "$prefix-${UUID.randomUUID()}@pangyeori.com",
            password = "encoded-password",
            nickname = "$prefix-${UUID.randomUUID().toString().take(8)}",
        ),
    )

    private fun saveReadyDebate(
        host: User,
        guest: User,
    ): Debate {
        val debate = debateRepository.save(
            Debate.create(
                host = host,
                title = "입장 테스트 토론",
                description = null,
                hostPosition = DebatePosition.PROS,
                turnTimeSeconds = 60,
                freeDebateTimeSeconds = 60,
                inviteToken = UUID.randomUUID().toString(),
            ),
        )
        debateUserRepository.save(
            DebateUser.create(
                debate = debate,
                user = host,
                role = DebateUserRole.HOST,
                position = DebatePosition.PROS,
            ),
        )
        val guestMember = DebateUser.create(
            debate = debate,
            user = guest,
            role = DebateUserRole.GUEST,
            position = DebatePosition.CONS,
        )
        guestMember.accept()
        debateUserRepository.save(guestMember)
        debate.acceptGuest(guest)
        return debateRepository.save(debate)
    }

    @AfterEach
    fun disconnectSessions() {
        sessions.forEach { runCatching { it.disconnect() } }
        sessions.clear()
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val TIMEOUT_SECONDS = 10L
    }
}
