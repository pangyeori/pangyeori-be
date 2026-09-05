package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.ResponseDsl
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class DebateControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    @Test
    fun `토론방을 생성한다`() {
        val email = "debate-host@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "토론방장",
                profileImageUrl = null,
            ),
        )
        val accessToken = authService.signIn(
            email = email,
            password = password,
        ).accessToken

        restDocs(mockMvc, "debates/create") {
            summary("토론방 생성")
            tag("Debates")
            request {
                post("/api/v1/debates")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("title", "AI는 인간을 대체할 것인가", "토론 주제")
                    field("description", "AI 기술의 영향을 토론합니다.", "토론 설명").optional()
                    field("hostPosition", "PROS", "개설자 포지션: PROS 또는 CONS")
                    field("turnTimeSeconds", 180, "턴당 발언 제한 시간(30~600초)")
                    field("freeDebateTimeSeconds", 600, "자유 토론 제한 시간(60~1800초)")
                }
            }
            response {
                status(201)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "생성된 토론방") {
                        field("id", "토론방 ID")
                        field("title", "토론 주제")
                        field("description", "토론 설명").optional()
                        field("hostPosition", "개설자 포지션")
                        field("guestPosition", "게스트 포지션")
                        field("status", "토론방 상태")
                        field("turnTimeSeconds", "턴당 발언 제한 시간")
                        field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                        field("inviteToken", "UUID v4 초대 토큰")
                        array("members", "토론방 참여자") {
                            field("userId", "사용자 ID")
                            field("nickname", "사용자 닉네임")
                            field("role", "참여자 역할")
                            field("position", "참여자 포지션")
                            field("status", "참여 상태")
                        }
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `턴 시간이 허용 범위를 벗어나면 INVALID_TURN_TIME을 반환한다`() {
        val email = "invalid-debate-host@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "검증방장",
                profileImageUrl = null,
            ),
        )
        val accessToken = authService.signIn(
            email = email,
            password = password,
        ).accessToken

        restDocs(mockMvc, "debates/create-invalid-turn-time") {
            summary("토론방 생성")
            tag("Debates")
            request {
                post("/api/v1/debates")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("title", "시간 검증 토론", "토론 주제")
                    field("hostPosition", "CONS", "개설자 포지션")
                    field("turnTimeSeconds", 29, "최솟값보다 작은 턴 시간")
                    field("freeDebateTimeSeconds", 600, "자유 토론 시간")
                }
            }
            response {
                status(400)
                businessErrorBody()
            }
        }
    }

    @Test
    fun `자유 토론 시간이 허용 범위를 벗어나면 INVALID_FREE_DEBATE_TIME을 반환한다`() {
        val accessToken = createHostAndSignIn(
            email = "invalid-free-time-host@pangyeori.com",
            nickname = "자유시간방장",
        )

        restDocs(mockMvc, "debates/create-invalid-free-debate-time") {
            summary("토론방 생성")
            tag("Debates")
            request {
                post("/api/v1/debates")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("title", "시간 검증 토론", "토론 주제")
                    field("hostPosition", "PROS", "개설자 포지션")
                    field("turnTimeSeconds", 180, "턴 시간")
                    field("freeDebateTimeSeconds", 1801, "최댓값보다 큰 자유 토론 시간")
                }
            }
            response {
                status(400)
                businessErrorBody()
            }
        }
    }

    @Test
    fun `포지션이 유효하지 않으면 INVALID_POSITION을 반환한다`() {
        val accessToken = createHostAndSignIn(
            email = "invalid-position-host@pangyeori.com",
            nickname = "포지션방장",
        )

        restDocs(mockMvc, "debates/create-invalid-position") {
            summary("토론방 생성")
            tag("Debates")
            request {
                post("/api/v1/debates")
                header("Authorization", "Bearer $accessToken")
                body {
                    field("title", "포지션 검증 토론", "토론 주제")
                    field("hostPosition", "INVALID", "유효하지 않은 개설자 포지션")
                    field("turnTimeSeconds", 180, "턴 시간")
                    field("freeDebateTimeSeconds", 600, "자유 토론 시간")
                }
            }
            response {
                status(400)
                businessErrorBody()
            }
        }
    }

    private fun createHostAndSignIn(
        email: String,
        nickname: String,
    ): String {
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = nickname,
                profileImageUrl = null,
            ),
        )

        return authService.signIn(
            email = email,
            password = password,
        ).accessToken
    }
}

private fun ResponseDsl.businessErrorBody() {
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
