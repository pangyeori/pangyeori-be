package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

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
}
