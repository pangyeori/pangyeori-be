package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.fixture.EntityAuditIntegrationTestSupport
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
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

    @Autowired
    private lateinit var debateService: DebateService

    @Autowired
    private lateinit var debateParticipationService: DebateParticipationService

    @Autowired
    private lateinit var debateRepository: DebateRepository

    @Autowired
    private lateinit var entityAuditIntegrationTestSupport: EntityAuditIntegrationTestSupport

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
        description: String? = null,
        hostPosition: DebatePosition = DebatePosition.PROS,
    ) = debateService.create(
        hostEmail = hostEmail,
        title = title,
        description = description,
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

    @Test
    fun `토론방을 생성한다`() {
        val email = "debate-host@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "토론방장",
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
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `턴 시간이 허용 범위를 벗어나면 400을 반환한다`() {
        val email = "invalid-debate-host@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "검증방장",
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
    fun `자유 토론 시간이 허용 범위를 벗어나면 400을 반환한다`() {
        val accessToken = issueAccessToken(
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
    fun `포지션이 유효하지 않으면 400을 반환한다`() {
        val accessToken = issueAccessToken(
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
    fun `게스트가 초대 링크로 참여를 요청한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "request-participation-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "참여요청방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "request-participation-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "참여요청게스트",
            ),
        )
        val guestToken = authService.signIn(
            email = guest.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "참여 요청 토론",
        )

        restDocs(mockMvc, "debates/request-participation") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(201)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "참여 요청 결과") {
                        field("debateId", "토론방 ID")
                        field("position", "게스트 포지션")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `개설자가 대기 중인 참여 요청 목록을 조회한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "status-list-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "상태목록방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "status-list-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "상태목록게스트",
            ),
        )
        val hostToken = authService.signIn(
            email = host.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "참여 요청 목록 조회 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )

        restDocs(mockMvc, "debates/get-status") {
            summary("토론방 참여 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "참여 상태") {
                        field("debateStatus", "토론방 상태")
                        field("guestStatus", "요청자의 참여 상태").optional()
                        array("requestList", "대기 중인 참여 요청 (호스트만 조회 가능)") {
                            field("userId", "사용자 ID")
                            field("nickname", "사용자 닉네임")
                            field("profileImageKey", "사용자 프로필 이미지 키").optional()
                            field("status", "참여 상태")
                            field("requestedAt", "참여 요청 시각")
                        }.optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `개설자가 대기 중인 게스트를 선택한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "accept-guest-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "게스트선택방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "accept-guest-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "게스트선택게스트",
            ),
        )
        val hostToken = authService.signIn(
            email = host.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "게스트 선택 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )

        restDocs(mockMvc, "debates/accept-guest") {
            summary("게스트 선택")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/guest/accept")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("userId", guest.id!!, "선택할 사용자 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "게스트 선택 결과") {
                        field("debateId", "토론방 ID")
                        field("status", "토론방 상태")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `게스트가 자신의 참여 상태를 조회한다`() {
        val hostEmail = "guest-status-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "게스트상태방장",
        )
        val guestEmail = "guest-status-guest@pangyeori.com"
        val guestToken = issueAccessToken(
            email = guestEmail,
            nickname = "게스트상태게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "게스트 상태 조회 검증",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guestEmail,
        )

        restDocs(mockMvc, "debates/get-status-guest") {
            summary("토론방 참여 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "참여 상태") {
                        field("debateStatus", "토론방 상태")
                        field("guestStatus", "요청자의 참여 상태").optional()
                        array("requestList", "대기 중인 참여 요청 (호스트만 조회 가능)") {
                            field("userId", "사용자 ID")
                            field("nickname", "사용자 닉네임")
                            field("profileImageKey", "사용자 프로필 이미지 키").optional()
                            field("status", "참여 상태")
                            field("requestedAt", "참여 요청 시각")
                        }.optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `대기 중인 참여 요청을 취소한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "cancel-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "취소방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "cancel-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "취소게스트",
            ),
        )
        val guestToken = authService.signIn(
            email = guest.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "참여 취소 토론",
            hostPosition = DebatePosition.CONS,
        )
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/debates/${debate.id}/invitations/request")
                .header("Authorization", "Bearer $guestToken"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)

        restDocs(mockMvc, "debates/cancel-participation") {
            summary("토론방 참여 요청 취소")
            tag("Debates")
            request {
                delete("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
            }
            response {
                status(204)
            }
        }
    }

    @Test
    fun `개설자가 자신의 토론방에 참여 요청하면 SELF_JOIN_NOT_ALLOWED를 반환한다`() {
        val email = "self-join-host@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "자기참여방장",
        )
        val debate = createDebate(
            hostEmail = email,
            title = "자기 참여 제한 토론",
        )

        restDocs(mockMvc, "debates/request-participation-self") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `대기 중인 요청이 없으면 NOT_IN_PENDING_STATUS를 반환한다`() {
        val hostEmail = "cancel-error-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "취소오류방장",
        )
        val guestToken = issueAccessToken(
            email = "cancel-error-guest@pangyeori.com",
            nickname = "취소오류게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "참여 취소 오류 토론",
            hostPosition = DebatePosition.CONS,
        )

        restDocs(mockMvc, "debates/cancel-participation-not-pending") {
            summary("토론방 참여 요청 취소")
            tag("Debates")
            request {
                delete("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `대기열에 없는 게스트를 선택하면 USER_NOT_FOUND_IN_QUEUE를 반환한다`() {
        val hostEmail = "accept-error-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "선택오류방장",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "게스트 선택 오류 토론",
        )

        restDocs(mockMvc, "debates/accept-guest-not-in-queue") {
            summary("게스트 선택")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/guest/accept")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("userId", "0000000000000", "대기열에 없는 사용자 ID")
                }
            }
            response {
                status(404)
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
    fun `참여 이력이 없는 사용자가 상태를 조회하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "status-error-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "상태오류방장",
        )
        val strangerToken = issueAccessToken(
            email = "status-error-stranger@pangyeori.com",
            nickname = "상태외부인",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "상태 접근 제한 토론",
        )

        restDocs(mockMvc, "debates/get-status-forbidden") {
            summary("토론방 참여 상태 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/{debateId}/status")
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

    @Test
    fun `참여 요청 시 토론방 상태에 맞는 충돌 오류를 반환한다`() {
        val guestToken = issueAccessToken(
            email = "request-state-guest@pangyeori.com",
            nickname = "요청상태게스트",
        )
        val states = listOf(
            DebateStatus.READY,
            DebateStatus.IN_PROGRESS,
            DebateStatus.FINISHED,
            DebateStatus.CANCELLED,
        )

        states.forEachIndexed { index, status ->
            val hostEmail = "request-state-host-$index@pangyeori.com"
            issueAccessToken(
                email = hostEmail,
                nickname = "요청상태방장$index",
            )
            val response = createDebate(
                hostEmail = hostEmail,
                title = "요청 상태 검증 토론 $index",
                hostPosition = DebatePosition.CONS,
            )
            val debate = debateRepository.findById(response.id).orElseThrow()
            debate.status = status
            debateRepository.saveAndFlush(debate)

            restDocs(mockMvc, "debates/request-participation-${status.code.lowercase()}") {
                summary("토론방 참여 요청")
                tag("Debates")
                request {
                    post("/api/v1/debates/{debateId}/invitations/request")
                    header("Authorization", "Bearer $guestToken")
                    pathParameters {
                        param("debateId", response.id, "토론방 ID")
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

    @Test
    fun `비개설자가 게스트를 선택하면 접근 권한 오류를 반환한다`() {
        val hostEmail = "accept-forbidden-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "권한검증방장",
        )
        val guestToken = issueAccessToken(
            email = "accept-forbidden-guest@pangyeori.com",
            nickname = "권한검증게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "게스트 선택 권한 검증",
        )

        restDocs(mockMvc, "debates/accept-guest-forbidden") {
            summary("게스트 선택")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/guest/accept")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
                }
                body {
                    field("userId", "0000000000000", "선택할 사용자 ID")
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

    @Test
    fun `WAITING 상태가 아니면 게스트를 선택할 수 없다`() {
        val hostEmail = "accept-state-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "선택상태방장",
        )
        val response = createDebate(
            hostEmail = hostEmail,
            title = "게스트 선택 상태 검증",
            hostPosition = DebatePosition.CONS,
        )
        val debate = debateRepository.findById(response.id).orElseThrow()
        debate.status = DebateStatus.READY
        debateRepository.saveAndFlush(debate)

        restDocs(mockMvc, "debates/accept-guest-not-waiting") {
            summary("게스트 선택")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/guest/accept")
                header("Authorization", "Bearer $hostToken")
                pathParameters {
                    param("debateId", response.id, "토론방 ID")
                }
                body {
                    field("userId", "0000000000000", "선택할 사용자 ID")
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
    fun `이미 대기 중인 게스트가 다시 참여 요청하면 409를 반환한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "already-pending-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "중복요청방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "already-pending-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "중복요청게스트",
            ),
        )
        val guestToken = authService.signIn(
            email = guest.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "중복 참여 요청 검증",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )

        restDocs(mockMvc, "debates/request-participation-already-pending") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `참여 요청을 취소한 게스트가 다시 요청하면 409를 반환한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "already-cancelled-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "취소재요청방장",
            ),
        )
        val guest = userRepository.save(
            User.create(
                email = "already-cancelled-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "취소재요청게스트",
            ),
        )
        val guestToken = authService.signIn(
            email = guest.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "취소 후 재요청 검증",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )
        debateParticipationService.cancelParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )

        restDocs(mockMvc, "debates/request-participation-already-cancelled") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `다른 게스트가 선택된 후 거절된 사용자의 재요청을 제한한다`() {
        val password = "password123!"
        val host = userRepository.save(
            User.create(
                email = "rejected-request-host@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "거절검증방장",
            ),
        )
        val rejectedGuest = userRepository.save(
            User.create(
                email = "rejected-request-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "거절된게스트",
            ),
        )
        val selectedGuest = userRepository.save(
            User.create(
                email = "selected-request-guest@pangyeori.com",
                password = passwordEncoder.encode(password)!!,
                nickname = "선택된게스트",
            ),
        )
        val rejectedToken = authService.signIn(
            email = rejectedGuest.email,
            password = password,
        ).accessToken
        val debate = createDebate(
            hostEmail = host.email,
            title = "거절 후 재요청 검증",
            hostPosition = DebatePosition.CONS,
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = rejectedGuest.email,
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = selectedGuest.email,
        )
        debateParticipationService.acceptGuest(
            debateId = debate.id,
            hostEmail = host.email,
            guestUserId = selectedGuest.id!!,
        )

        restDocs(mockMvc, "debates/request-participation-already-rejected") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $rejectedToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `만료된 토론방에 참여 요청하면 INVITE_TOKEN_EXPIRED를 반환한다`() {
        val hostEmail = "expired-request-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "만료요청방장",
        )
        val guestToken = issueAccessToken(
            email = "expired-request-guest@pangyeori.com",
            nickname = "만료요청게스트",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "만료 참여 요청 검증",
        )
        entityAuditIntegrationTestSupport.backdateCreatedAt(
            entityClass = Debate::class,
            id = debate.id,
            hours = 48,
        )

        restDocs(mockMvc, "debates/request-participation-expired") {
            summary("토론방 참여 요청")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/invitations/request")
                header("Authorization", "Bearer $guestToken")
                pathParameters {
                    param("debateId", debate.id, "토론방 ID")
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
    fun `게스트 선택 시 사용자 ID가 없으면 입력값 검증 오류를 반환한다`() {
        val hostEmail = "accept-validation-host@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "선택검증방장",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "게스트 선택 입력값 검증",
            hostPosition = DebatePosition.CONS,
        )

        restDocs(mockMvc, "debates/accept-guest-invalid-user-id") {
            summary("게스트 선택")
            tag("Debates")
            request {
                post("/api/v1/debates/{debateId}/guest/accept")
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
                            field("field", "검증 실패 필드")
                            field("message", "검증 실패 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `내가 속한 토론방 목록을 최신순으로 조회한다`() {
        val myEmail = "my-debates@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "목록조회자",
        )
        val firstGuestEmail = "my-debates-guest-1@pangyeori.com"
        issueAccessToken(
            email = firstGuestEmail,
            nickname = "목록게스트1",
        )
        val secondGuestEmail = "my-debates-guest-2@pangyeori.com"
        issueAccessToken(
            email = secondGuestEmail,
            nickname = "목록게스트2",
        )
        val otherHostEmail = "my-debates-other-host@pangyeori.com"
        issueAccessToken(
            email = otherHostEmail,
            nickname = "목록타방장",
        )
        createDebate(
            hostEmail = myEmail,
            title = "첫 번째 토론",
        ).also {
            joinAsGuest(
                debateId = it.id,
                hostEmail = myEmail,
                guestEmail = firstGuestEmail,
            )
        }
        val middle = createDebate(
            hostEmail = myEmail,
            title = "두 번째 토론",
        )
        joinAsGuest(
            debateId = middle.id,
            hostEmail = myEmail,
            guestEmail = secondGuestEmail,
        )
        val newest = createDebate(
            hostEmail = otherHostEmail,
            title = "세 번째 토론",
        )
        joinAsGuest(
            debateId = newest.id,
            hostEmail = otherHostEmail,
            guestEmail = myEmail,
        )

        restDocs(mockMvc, "debates/get-my-list") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
                queryParameters {
                    param(
                        "status",
                        null,
                        "토론방 상태 필터 (WAITING, READY, IN_PROGRESS, PAUSED, FINISHED, CANCELLED)"
                    ).optional()
                    param("role", null, "내 역할 필터 (HOST, GUEST)").optional()
                    param("keyword", null, "토론 주제·설명 검색어").optional()
                    param("cursor", null, "이전 응답의 nextCursor").optional()
                    param("pageSize", "2", "페이지 크기 (기본 20, 최대 50)").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            obj("opponent", "상대방 정보") {
                                field("userId", "상대방 사용자 ID")
                                field("nickname", "상대방 닉네임")
                                field("profileImageKey", "상대방 프로필 이미지 키").optional()
                            }
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `커서를 지정해 내 토론방 목록의 다음 페이지를 조회한다`() {
        val myEmail = "my-debates-cursor@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "커서목록조회자",
        )
        val firstGuestEmail = "my-debates-cursor-guest-1@pangyeori.com"
        issueAccessToken(
            email = firstGuestEmail,
            nickname = "커서목록게스트1",
        )
        val secondGuestEmail = "my-debates-cursor-guest-2@pangyeori.com"
        issueAccessToken(
            email = secondGuestEmail,
            nickname = "커서목록게스트2",
        )
        val otherHostEmail = "my-debates-cursor-other-host@pangyeori.com"
        issueAccessToken(
            email = otherHostEmail,
            nickname = "커서목록타방장",
        )
        createDebate(
            hostEmail = myEmail,
            title = "첫 번째 토론",
        ).also {
            joinAsGuest(
                debateId = it.id,
                hostEmail = myEmail,
                guestEmail = firstGuestEmail,
            )
        }
        val middle = createDebate(
            hostEmail = myEmail,
            title = "두 번째 토론",
        )
        joinAsGuest(
            debateId = middle.id,
            hostEmail = myEmail,
            guestEmail = secondGuestEmail,
        )
        val newest = createDebate(
            hostEmail = otherHostEmail,
            title = "세 번째 토론",
        )
        joinAsGuest(
            debateId = newest.id,
            hostEmail = otherHostEmail,
            guestEmail = myEmail,
        )

        restDocs(mockMvc, "debates/get-my-list-next-page") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
                queryParameters {
                    param("cursor", middle.id, "이전 응답의 nextCursor").optional()
                    param("pageSize", "2", "페이지 크기 (기본 20, 최대 50)").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            obj("opponent", "상대방 정보") {
                                field("userId", "상대방 사용자 ID")
                                field("nickname", "상대방 닉네임")
                                field("profileImageKey", "상대방 프로필 이미지 키").optional()
                            }
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `상태와 역할과 검색어로 내 토론방 목록을 필터링한다`() {
        val myEmail = "my-debates-filter@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "필터조회자",
        )
        val otherHostEmail = "my-debates-filter-host@pangyeori.com"
        issueAccessToken(
            email = otherHostEmail,
            nickname = "필터타방장",
        )
        createDebate(
            hostEmail = myEmail,
            title = "첫 번째 토론",
            description = "[필터매칭] status=WAITING, role=HOST, keyword=필터매칭 조건에 모두 일치해 조회됩니다.",
        )
        createDebate(
            hostEmail = myEmail,
            title = "두 번째 토론",
        )
        val requested = createDebate(
            hostEmail = otherHostEmail,
            title = "세 번째 토론",
            description = "[필터매칭] keyword는 일치하지만 role=GUEST라 조회되지 않습니다.",
        )
        debateParticipationService.requestParticipation(
            debateId = requested.id,
            userEmail = myEmail,
        )

        restDocs(mockMvc, "debates/get-my-list-filtered") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
                queryParameters {
                    param(
                        "status",
                        "WAITING",
                        "토론방 상태 필터 (WAITING, READY, IN_PROGRESS, PAUSED, FINISHED, CANCELLED)"
                    ).optional()
                    param("role", "HOST", "내 역할 필터 (HOST, GUEST)").optional()
                    param("keyword", "필터매칭", "토론 주제·설명 검색어").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            field("opponent", "상대방 정보 (아직 매칭되지 않았으면 null)").optional()
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `status를 여러 번 지정하면 해당 상태들을 모두 포함해 조회한다`() {
        val myEmail = "my-debates-multi-status@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "다중상태조회자",
        )
        createDebate(
            hostEmail = myEmail,
            title = "대기 중인 토론",
            description = "[다중상태] status=WAITING&status=READY 중 WAITING에 해당해 조회됩니다.",
        )
        val ready = createDebate(
            hostEmail = myEmail,
            title = "매칭된 토론",
            description = "[다중상태] status=WAITING&status=READY 중 READY에 해당해 조회됩니다.",
        )
        val readyDebate = debateRepository.findById(ready.id).orElseThrow()
        readyDebate.status = DebateStatus.READY
        debateRepository.saveAndFlush(readyDebate)
        val finished = createDebate(
            hostEmail = myEmail,
            title = "종료된 토론",
            description = "[다중상태] status=WAITING&status=READY 어느 쪽에도 해당하지 않아 조회되지 않습니다.",
        )
        val finishedDebate = debateRepository.findById(finished.id).orElseThrow()
        finishedDebate.status = DebateStatus.FINISHED
        debateRepository.saveAndFlush(finishedDebate)

        restDocs(mockMvc, "debates/get-my-list-multi-status") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
                queryParameters {
                    param("status", "WAITING", "토론방 상태 필터. 같은 이름으로 여러 번 지정하면 해당 상태들을 모두 포함해 조회한다").optional()
                    param("status", "READY", "토론방 상태 필터. 같은 이름으로 여러 번 지정하면 해당 상태들을 모두 포함해 조회한다").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            field("opponent", "상대방 정보 (아직 매칭되지 않았으면 null)").optional()
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `거절되거나 취소한 참여 요청 토론방은 목록에서 제외한다`() {
        val myEmail = "my-debates-excluded@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "제외조회자",
        )
        val hostEmail = "my-debates-excluded-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "제외방장",
        )
        val selectedGuestEmail = "my-debates-excluded-selected@pangyeori.com"
        issueAccessToken(
            email = selectedGuestEmail,
            nickname = "제외선택게스트",
        )
        val cancelled = createDebate(
            hostEmail = hostEmail,
            title = "참여를 취소할 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = cancelled.id,
            userEmail = myEmail,
        )
        debateParticipationService.cancelParticipation(
            debateId = cancelled.id,
            userEmail = myEmail,
        )
        val rejected = createDebate(
            hostEmail = hostEmail,
            title = "거절될 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = rejected.id,
            userEmail = myEmail,
        )
        joinAsGuest(
            debateId = rejected.id,
            hostEmail = hostEmail,
            guestEmail = selectedGuestEmail,
        )

        restDocs(mockMvc, "debates/get-my-list-empty") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        field("items", "토론방 목록")
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `아직 게스트로 선택되지 않은 참여 요청도 목록에 조회된다`() {
        val myEmail = "my-debates-pending@pangyeori.com"
        val myToken = issueAccessToken(
            email = myEmail,
            nickname = "대기조회자",
        )
        val hostEmail = "my-debates-pending-host@pangyeori.com"
        issueAccessToken(
            email = hostEmail,
            nickname = "대기방장",
        )
        val pending = createDebate(
            hostEmail = hostEmail,
            title = "아직 선택되지 않은 토론",
            description = "참여 요청은 보냈지만 호스트가 아직 게스트를 선택하지 않아도 목록에 조회됩니다.",
        )
        debateParticipationService.requestParticipation(
            debateId = pending.id,
            userEmail = myEmail,
        )

        restDocs(mockMvc, "debates/get-my-list-pending") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $myToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            obj("opponent", "상대방 정보") {
                                field("userId", "상대방 사용자 ID")
                                field("nickname", "상대방 닉네임")
                                field("profileImageKey", "상대방 프로필 이미지 키").optional()
                            }
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `호스트 입장에서도 아직 게스트를 선택하지 않은 토론방이 목록에 조회된다`() {
        val hostEmail = "my-debates-host-pending@pangyeori.com"
        val hostToken = issueAccessToken(
            email = hostEmail,
            nickname = "대기방장",
        )
        val guestEmail = "my-debates-host-pending-guest@pangyeori.com"
        issueAccessToken(
            email = guestEmail,
            nickname = "대기요청자",
        )
        val debate = createDebate(
            hostEmail = hostEmail,
            title = "아직 게스트를 선택하지 않은 토론",
            description = "참여 요청이 들어왔지만 아직 게스트를 선택하지 않아도 개설자 목록에 조회됩니다.",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guestEmail,
        )

        restDocs(mockMvc, "debates/get-my-list-host-pending") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $hostToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 토론방 목록") {
                        array("items", "토론방 목록") {
                            field("debateId", "토론방 ID")
                            field("title", "토론 주제")
                            field("description", "토론 설명").optional()
                            field("debateStatus", "토론방 상태")
                            field("currentStage", "현재 진행 단계")
                            field("myRole", "내 역할")
                            field("myPosition", "내 포지션")
                            field("opponent", "상대방 정보 (아직 게스트를 선택하지 않았으면 null)").optional()
                            field("turnTimeSeconds", "턴당 발언 제한 시간")
                            field("freeDebateTimeSeconds", "자유 토론 제한 시간")
                            field("createdAt", "토론방 생성 시각")
                        }
                        field("nextCursor", "다음 페이지 커서").optional()
                        field("hasNext", "다음 페이지 존재 여부")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `유효하지 않은 상태로 내 토론방 목록을 조회하면 400을 반환한다`() {
        val accessToken = issueAccessToken(
            email = "my-debates-invalid-status@pangyeori.com",
            nickname = "잘못된상태조회자",
        )

        restDocs(mockMvc, "debates/get-my-list-invalid-status") {
            summary("내 토론방 목록 조회")
            tag("Debates")
            request {
                get("/api/v1/debates/me")
                header("Authorization", "Bearer $accessToken")
                queryParameters {
                    param("status", "UNKNOWN", "유효하지 않은 토론방 상태")
                }
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
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }
}
