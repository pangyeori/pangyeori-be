package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.repository.DebateFreeMessageRepository
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

class DebateFreeMessageControllerTest : RestDocsMvcTest() {

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
    private lateinit var debateFreeMessageRepository: DebateFreeMessageRepository

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
    fun `자유 토론 메시지를 제출 시각 역순으로 조회한다`() {
        val hostEmail = "free-message-get-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "자유토론조회방장",
        )
        val guestEmail = "free-message-get-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "자유토론조회게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "자유 토론 조회 검증 토론",
        )
        joinAsGuest(
            debateId = debate.id,
            hostEmail = hostEmail,
            guestEmail = guestEmail,
        )
        val started = startFreeDebate(debate.id)
        val host = userRepository.findByEmail(
            email = hostEmail,
        )!!
        debateFreeMessageRepository.save(
            DebateFreeMessage.create(
                debate = started,
                user = host,
                role = DebateUserRole.HOST,
                content = "먼저 보낸 메시지",
            ),
        )
        debateFreeMessageRepository.save(
            DebateFreeMessage.create(
                debate = started,
                user = host,
                role = DebateUserRole.HOST,
                content = "나중에 보낸 메시지",
            ),
        )

        restDocs(mockMvc, "debate-free-messages/get") {
            summary("자유 토론 메시지 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/free-messages")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                queryParameters {
                    param("cursor", null, "이전 응답의 nextCursor").optional()
                    param("pageSize", "10", "페이지 크기 (기본 20, 최대 50)").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "커서 기반 페이지네이션 응답") {
                        array("items", "자유 토론 메시지 목록 (최신순)") {
                            field("id", "메시지 ID")
                            field("role", "보낸 사람의 역할 (HOST, GUEST)")
                            field("content", "메시지 내용")
                            field("createdAt", "전송 시각")
                        }
                        field("nextCursor", "다음 페이지 조회에 사용할 커서. 다음 페이지가 없으면 null").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `참여 이력이 없는 사용자가 자유 토론 메시지를 조회하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "free-message-get-forbidden-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "자유토론조회거부방장",
        )
        val strangerToken = issueAccessToken(
            email = "free-message-get-forbidden-stranger@pangyeori.com",
            nickname = "자유토론조회거부타인",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "자유 토론 조회 권한 검증 토론",
        )

        restDocs(mockMvc, "debate-free-messages/get-forbidden") {
            summary("자유 토론 메시지 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/free-messages")
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
