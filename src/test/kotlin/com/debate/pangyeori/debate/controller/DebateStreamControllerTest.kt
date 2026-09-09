package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.debate.service.DebateStreamService
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DebateStreamControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var debateService: DebateService

    @Autowired
    private lateinit var debateParticipationService: DebateParticipationService

    @Autowired
    private lateinit var debateStreamService: DebateStreamService

    @Autowired
    private lateinit var debateSseRegistry: DebateSseRegistry

    private fun issueAccessToken(
        email: String,
        nickname: String,
    ): String {
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = nickname,
            ),
        )

        return authService.signIn(
            email = email,
            password = password,
        ).accessToken
    }

    @Test
    fun `참여 요청한 게스트가 상태 스트림 티켓을 발급받는다`() {
        val hostEmail = "stream-ticket-host@pangyeori.com"
        val guestEmail = "stream-ticket-guest@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "스트림방장",
        )
        val guestToken = issueAccessToken(
            email = guestEmail,
            nickname = "스트림게스트",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "스트림 티켓 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guestEmail,
        )

        restDocs(mockMvc, "debates/issue-stream-ticket") {
            summary("토론 상태 스트림 티켓 발급")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/status/stream-tickets")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(201)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "스트림 티켓") {
                        field("ticket", "SSE 연결 시 쿼리 파라미터로 전달하는 일회성 티켓").mask("<ticket>")
                        field("expiresInSeconds", "티켓 만료까지 남은 초")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `참여 이력이 없는 사용자가 스트림 티켓을 요청하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "stream-ticket-host2@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "스트림방장2",
        )
        val outsiderToken = issueAccessToken(
            email = "stream-ticket-outsider@pangyeori.com",
            nickname = "스트림외부인",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "스트림 티켓 접근 제한 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )

        restDocs(mockMvc, "debates/issue-stream-ticket-forbidden") {
            summary("토론 상태 스트림 티켓 발급")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/status/stream-tickets")
                header("Authorization", "Bearer $outsiderToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(403)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    // 스트리밍 응답이라 restDocs { } DSL(단일 perform) 로는 다룰 수 없어, async dispatch 를 직접 처리한다.
    // 연결과 최초 snapshot 만 확인하는 최소 케이스이고, 이후 이벤트 계약은 AsyncAPI 문서가 담당한다.
    @Test
    fun `유효한 티켓으로 상태 스트림을 요청하면 200과 이벤트 스트림을 반환한다`() {
        val hostEmail = "stream-ok-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "스트림정상방장",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "정상 스트림 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        val streamTicket = debateStreamService.issueTicket(
            debateId = debate.id,
            userEmail = hostEmail,
        )

        val connected = mockMvc.perform(
            get("/api/v1/debates/{debateId}/status/stream", debate.id)
                .param("ticket", streamTicket.ticket)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        debateSseRegistry.dispatch(
            DebateStreamMessage(
                debateId = debate.id,
                target = DebateStreamMessage.Target.all(),
                eventName = null,
                data = null,
                close = true,
            ),
        )

        mockMvc.perform(asyncDispatch(connected))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:snapshot")))
            .andDo(
                document(
                    "debates/stream-status",
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Debates")
                            .summary("토론 상태 스트림 구독")
                            .description(
                                "유효한 일회성 티켓으로 연결하면 200과 함께 text/event-stream으로 참여 상태 이벤트를 흘려보낸다. " +
                                        "이벤트 종류와 payload 스키마는 AsyncAPI 문서를 참고한다.",
                            )
                            .pathParameters(parameterWithName("debateId").description("토론방 ID"))
                            .queryParameters(
                                parameterWithName("ticket").description("스트림 티켓 발급 API로 받은 일회성 티켓"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    fun `게스트가 유효한 티켓으로 상태 스트림을 요청하면 200과 게스트 관점 스냅샷을 반환한다`() {
        val hostEmail = "stream-ok-guest-host@pangyeori.com"
        val guestEmail = "stream-ok-guest@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "게스트관점방장",
        )
        issueAccessToken(
            email = guestEmail,
            nickname = "게스트관점게스트",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "게스트 관점 스트림 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guestEmail,
        )
        val streamTicket = debateStreamService.issueTicket(
            debateId = debate.id,
            userEmail = guestEmail,
        )

        val connected = mockMvc.perform(
            get("/api/v1/debates/{debateId}/status/stream", debate.id)
                .param("ticket", streamTicket.ticket)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        debateSseRegistry.dispatch(
            DebateStreamMessage(
                debateId = debate.id,
                target = DebateStreamMessage.Target.all(),
                eventName = null,
                data = null,
                close = true,
            ),
        )

        mockMvc.perform(asyncDispatch(connected))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:snapshot")))
            .andExpect(content().string(containsString("\"guestStatus\":\"PENDING\"")))
            .andExpect(content().string(containsString("\"requestList\":null")))
            .andDo(
                document(
                    "debates/stream-status-guest",
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Debates")
                            .summary("토론 상태 스트림 구독")
                            .description(
                                "유효한 일회성 티켓으로 연결하면 200과 함께 text/event-stream으로 참여 상태 이벤트를 흘려보낸다. " +
                                        "이벤트 종류와 payload 스키마는 AsyncAPI 문서를 참고한다.",
                            )
                            .pathParameters(parameterWithName("debateId").description("토론방 ID"))
                            .queryParameters(
                                parameterWithName("ticket").description("스트림 티켓 발급 API로 받은 일회성 티켓"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    fun `티켓 없이 상태 스트림을 요청하면 401을 반환한다`() {
        val hostEmail = "stream-noticket-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "무티켓방장",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "무티켓 스트림 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )

        restDocs(mockMvc, "debates/stream-status-missing-ticket") {
            summary("토론 상태 스트림 구독")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status/stream")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `유효하지 않은 티켓으로 상태 스트림을 요청하면 401을 반환한다`() {
        val hostEmail = "stream-badticket-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "잘못된티켓방장",
        )
        val debate = debateService.create(
            hostEmail = hostEmail,
            title = "잘못된 티켓 스트림 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )

        restDocs(mockMvc, "debates/stream-status-invalid-ticket") {
            summary("토론 상태 스트림 구독")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status/stream")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                queryParameters {
                    param("ticket", "invalid-ticket-value", "존재하지 않거나 이미 사용된 티켓")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `다른 토론방으로 발급된 티켓으로 상태 스트림을 요청하면 401을 반환한다`() {
        val hostEmail = "stream-mismatch-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "다른방티켓방장",
        )
        val ticketDebate = debateService.create(
            hostEmail = hostEmail,
            title = "티켓 발급 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        val targetDebate = debateService.create(
            hostEmail = hostEmail,
            title = "티켓 대상 토론",
            description = null,
            hostPosition = "PROS",
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        val streamTicket = debateStreamService.issueTicket(
            debateId = ticketDebate.id,
            userEmail = hostEmail,
        )

        restDocs(mockMvc, "debates/stream-status-debate-mismatch") {
            summary("토론 상태 스트림 구독")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status/stream")
                pathParameters {
                    param("debateId", targetDebate.id, "티켓이 발급된 토론방과 다른 토론방 ID")
                }
                queryParameters {
                    param("ticket", streamTicket.ticket, "다른 토론방으로 발급된 티켓")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }
}
