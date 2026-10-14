package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.service.DebateConnectionService
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class DebateConnectionControllerTest : RestDocsMvcTest() {

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
    private lateinit var debateConnectionService: DebateConnectionService

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

    private fun enterByEmail(
        debateId: String,
        email: String,
        sessionId: String,
    ) {
        val user = userRepository.findByEmail(
            email = email,
        )!!
        debateConnectionService.enter(
            debateId = debateId,
            userId = user.id!!,
            sessionId = sessionId,
            subscriptionId = "sub-0",
        )
    }

    @Test
    fun `상대방이 입장해 있으면 접속 여부를 true로 응답한다`() {
        val hostEmail = "presence-opponent-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "입장조회방장",
        )
        val guestEmail = "presence-opponent-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "입장조회게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "상대방 입장 조회 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        enterByEmail(
            debateId = debate.id,
            email = guestEmail,
            sessionId = "presence-opponent-session",
        )

        restDocs(mockMvc, "debates/get-presence") {
            summary("토론방 입장 여부 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/presence")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "입장 여부") {
                        field("hostConnected", "개설자 접속 여부")
                        field("guestConnected", "게스트 접속 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `아직 아무도 입장하지 않았으면 양쪽 모두 false로 응답한다`() {
        val hostEmail = "presence-empty-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "미입장방장",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "미입장 조회 토론",
        )

        restDocs(mockMvc, "debates/get-presence-empty") {
            summary("토론방 입장 여부 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/presence")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "입장 여부") {
                        field("hostConnected", "개설자 접속 여부")
                        field("guestConnected", "게스트 접속 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `참여 이력이 없는 사용자가 입장 여부를 조회하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "presence-forbidden-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "입장조회거부방장",
        )
        val strangerToken = issueAccessToken(
            email = "presence-forbidden-stranger@pangyeori.com",
            nickname = "입장조회타인",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "입장 조회 접근 거부 검증",
        )

        restDocs(mockMvc, "debates/get-presence-forbidden") {
            summary("토론방 입장 여부 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/presence")
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
