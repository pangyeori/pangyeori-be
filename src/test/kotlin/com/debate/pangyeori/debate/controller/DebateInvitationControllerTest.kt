package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class DebateInvitationControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var debateService: DebateService

    @Autowired
    private lateinit var debateRepository: DebateRepository

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

    @Test
    fun `유효한 초대 토큰으로 초대 정보를 조회한다`() {
        val hostEmail = "invitation-get-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "초대조회방장",
        )
        val guestToken = issueAccessToken(
            email = "invitation-get-guest@pangyeori.com",
            nickname = "초대조회게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "초대 정보 조회 토론",
        )

        restDocs(mockMvc, "debate-invitations/get") {
            summary("초대 링크 유효성 확인")
            tag("Debates")
            request {
                get("/api/v1/debate-invitations/{token}")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("token", debate.inviteToken, "초대 토큰")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "초대 정보") {
                        field("debateId", "토론방 ID")
                        field("title", "토론 주제")
                        field("guestPosition", "게스트 포지션")
                        field("debateStatus", "토론방 상태")
                        field("guestStatus", "요청자의 참여 상태").optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `만료된 초대 토큰이면 INVITE_TOKEN_EXPIRED를 반환한다`() {
        val accessToken = issueAccessToken(
            email = "expired-invitation@pangyeori.com",
            nickname = "만료확인자",
        )

        restDocs(mockMvc, "debate-invitations/get-expired") {
            summary("초대 링크 유효성 확인")
            tag("Debates")
            request {
                get("/api/v1/debate-invitations/{token}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("token", "expired-token", "만료되거나 존재하지 않는 초대 토큰")
                }
            }
            response {
                status(410)
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
    fun `초대 링크 조회 시 토론방 상태에 맞는 충돌 오류를 반환한다`() {
        val guestToken = issueAccessToken(
            email = "invitation-state-guest@pangyeori.com",
            nickname = "초대상태게스트",
        )
        val states = listOf(
            DebateStatus.READY,
            DebateStatus.IN_PROGRESS,
            DebateStatus.FINISHED,
            DebateStatus.CANCELLED,
        )

        states.forEachIndexed { index, status ->
            val hostEmail = "invitation-state-host-$index@pangyeori.com"
            issueAccessToken(
                email = hostEmail,
                nickname = "초대상태방장$index",
            )
            val response = createDebate(
                hostEmail = hostEmail,
                title = "초대 상태 검증 토론 $index",
            )
            val debate = debateRepository.findById(response.id).orElseThrow()
            debate.status = status
            debateRepository.saveAndFlush(debate)

            restDocs(mockMvc, "debate-invitations/get-${status.code.lowercase()}") {
                summary("초대 링크 유효성 확인")
                tag("Debates")
                request {
                    get("/api/v1/debate-invitations/{token}")
                    header("Authorization", "Bearer $guestToken")
                    pathParameters {
                        param("token", response.inviteToken, "초대 토큰")
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
    }
}
