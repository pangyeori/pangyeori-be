package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.repository.DebateEarlyEndRedisRepository
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

class DebateEarlyEndControllerTest : RestDocsMvcTest() {

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
    private lateinit var debateEarlyEndRedisRepository: DebateEarlyEndRedisRepository

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
    ) = debateService.create(
        hostEmail = hostEmail,
        title = title,
        description = null,
        hostPosition = DebatePosition.PROS,
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

    private fun startFreeDebate(
        debateId: String,
    ): Debate {
        val debate = debateRepository.findById(debateId).orElseThrow()
        debate.start()
        debate.advanceStage(next = DebateStage.FREE)

        return debateRepository.saveAndFlush(debate)
    }

    @Test
    fun `진행 중인 조기 종료 제안이 없으면 pending을 false로 응답한다`() {
        val hostEmail = "early-end-get-empty-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "조기종료조회방장",
        )
        val guestEmail = "early-end-get-empty-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "조기종료조회게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "조기 종료 조회 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        startFreeDebate(debate.id)

        restDocs(mockMvc, "debate-early-end/get-empty") {
            summary("토론방 조기 종료 제안 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/early-end")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "조기 종료 제안 상태") {
                        field("pending", "진행 중인 제안 존재 여부")
                        field("proposerRole", "제안자 역할 (HOST, GUEST). 진행 중인 제안이 없으면 null").optional()
                        field("expiresAt", "제안 만료 시각. 진행 중인 제안이 없으면 null").optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `진행 중인 조기 종료 제안이 있으면 제안자 역할과 함께 응답한다`() {
        val hostEmail = "early-end-get-pending-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "조기종료조회중방장",
        )
        val guestEmail = "early-end-get-pending-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "조기종료조회중게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "조기 종료 진행 중 조회 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        startFreeDebate(debate.id)
        val host = userRepository.findByEmail(
            email = hostEmail,
        )!!
        debateEarlyEndRedisRepository.propose(
            debateId = debate.id,
            proposerId = host.id!!,
            proposerRole = DebateUserRole.HOST,
        )

        restDocs(mockMvc, "debate-early-end/get-pending") {
            summary("토론방 조기 종료 제안 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/early-end")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "조기 종료 제안 상태") {
                        field("pending", "진행 중인 제안 존재 여부")
                        field("proposerRole", "제안자 역할 (HOST, GUEST). 진행 중인 제안이 없으면 null").optional()
                        field("expiresAt", "제안 만료 시각. 진행 중인 제안이 없으면 null").optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `참여 이력이 없는 사용자가 조기 종료 제안 상태를 조회하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "early-end-get-forbidden-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "조기종료조회거부방장",
        )
        val strangerToken = issueAccessToken(
            email = "early-end-get-forbidden-stranger@pangyeori.com",
            nickname = "조기종료조회거부타인",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "조기 종료 조회 권한 검증 토론",
        )

        restDocs(mockMvc, "debate-early-end/get-forbidden") {
            summary("토론방 조기 종료 제안 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/early-end")
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
