package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class DebateHistoryControllerTest : RestDocsMvcTest() {

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
    private lateinit var debateRepository: DebateRepository

    @Autowired
    private lateinit var debateHistoryRepository: DebateHistoryRepository

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

    private fun createDebate(
        hostEmail: String,
        title: String,
        hostPosition: DebatePosition = DebatePosition.PROS,
    ) = debateService.create(
        hostEmail = hostEmail,
        title = title,
        description = null,
        hostPosition = hostPosition,
        turnTimeSeconds = 180,
        freeDebateTimeSeconds = 600,
    )

    private fun joinAsGuest(
        debateId: String,
        hostEmail: String,
        guestEmail: String,
    ) {
        debateParticipationService.requestParticipation(
            debateId = debateId,
            userEmail = guestEmail,
        )
        val guest = userRepository.findByEmail(
            email = guestEmail,
        )!!
        debateParticipationService.acceptGuest(
            debateId = debateId,
            hostEmail = hostEmail,
            guestUserId = guest.id!!,
        )
    }

    private fun startDebate(
        debateId: String,
    ): Debate {
        val debate = debateRepository.findById(debateId).orElseThrow()
        debate.start()

        return debateRepository.saveAndFlush(debate)
    }

    @Test
    fun `내 턴에 발언을 제출하면 기록을 저장하고 다음 단계로 넘어간다`() {
        val hostEmail = "history-submit-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "발언제출방장",
        )
        val guestEmail = "history-submit-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "발언제출게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "발언 제출 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        startDebate(debate.id)

        restDocs(mockMvc, "debate-histories/submit") {
            summary("발언 제출")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("content", "저는 이 주제에 찬성합니다.", "발언 내용(최대 1000자)")
                }
            }
            response {
                status(201)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "제출된 발언 기록") {
                        field("id", "발언 기록 ID")
                        field("stage", "발언 단계")
                        field("content", "발언 내용").optional()
                        field("createdAt", "제출 시각")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `발언 내용이 비어 있으면 입력값 검증 오류를 반환한다`() {
        val hostEmail = "history-submit-blank-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "빈발언방장",
        )
        val guestEmail = "history-submit-blank-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "빈발언게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "빈 발언 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        startDebate(debate.id)

        restDocs(mockMvc, "debate-histories/submit-blank-content") {
            summary("발언 제출")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body { }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `내 턴이 아니면 발언을 제출할 수 없다`() {
        val hostEmail = "history-not-your-turn-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "순서검증방장",
        )
        val guestEmail = "history-not-your-turn-guest@pangyeori.com"
        val guestToken = issueAccessToken(
            email = guestEmail,
            nickname = "순서검증게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "발언 순서 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        startDebate(debate.id)

        restDocs(mockMvc, "debate-histories/submit-not-your-turn") {
            summary("발언 제출")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("content", "아직 제 차례가 아닙니다.", "발언 내용(최대 1000자)")
                }
            }
            response {
                status(422)
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
    fun `같은 단계에 이미 제출된 발언이 있으면 409를 반환한다`() {
        val hostEmail = "history-already-submitted-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "중복제출방장",
        )
        val guestEmail = "history-already-submitted-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "중복제출게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "중복 제출 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        val started = startDebate(debate.id)
        val host = userRepository.findByEmail(
            email = hostEmail,
        )!!
        debateHistoryRepository.save(
            DebateHistory.create(
                debate = started,
                user = host,
                stage = DebateStage.OPENING_PROS,
                content = "이미 제출된 발언",
            ),
        )

        restDocs(mockMvc, "debate-histories/submit-already-submitted") {
            summary("발언 제출")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("content", "다시 제출해봅니다.", "발언 내용(최대 1000자)")
                }
            }
            response {
                status(409)
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
    fun `토론이 시작되지 않았으면 발언을 제출할 수 없다`() {
        val hostEmail = "history-not-ready-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "시작전방장",
        )
        val guestEmail = "history-not-ready-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "시작전게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "시작 전 발언 제출 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )

        restDocs(mockMvc, "debate-histories/submit-not-ready") {
            summary("발언 제출")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("content", "아직 시작 전인데 제출합니다.", "발언 내용(최대 1000자)")
                }
            }
            response {
                status(409)
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
    fun `발언 기록을 제출 순서대로 조회한다`() {
        val hostEmail = "history-get-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "기록조회방장",
        )
        val guestEmail = "history-get-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "기록조회게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "발언 기록 조회 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        val started = startDebate(debate.id)
        val host = userRepository.findByEmail(
            email = hostEmail,
        )!!
        debateHistoryRepository.save(
            DebateHistory.create(
                debate = started,
                user = host,
                stage = DebateStage.OPENING_PROS,
                content = "먼저 제출된 발언",
            ),
        )

        restDocs(mockMvc, "debate-histories/get") {
            summary("발언 기록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    array("data", "발언 기록 목록 (제출 시각순)") {
                        field("id", "발언 기록 ID")
                        field("stage", "발언 단계")
                        field("content", "발언 내용. 시간 초과로 무응답 처리된 경우 null").optional()
                        field("createdAt", "제출 시각")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `참여 이력이 없는 사용자가 발언 기록을 조회하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "history-get-forbidden-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "기록조회거부방장",
        )
        val strangerToken = issueAccessToken(
            email = "history-get-forbidden-stranger@pangyeori.com",
            nickname = "기록조회거부타인",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "발언 기록 조회 권한 검증 토론",
        )

        restDocs(mockMvc, "debate-histories/get-forbidden") {
            summary("발언 기록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/histories")
                header("Authorization", "Bearer $strangerToken")
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
}
