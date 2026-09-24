package com.debate.pangyeori.notification.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.notification.message.NotificationMessages
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class NotificationControllerTest : RestDocsMvcTest() {

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

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

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
    ): Debate {
        val response = debateService.create(
            hostEmail = hostEmail,
            title = title,
            description = null,
            hostPosition = DebatePosition.PROS,
            turnTimeSeconds = 180,
            freeDebateTimeSeconds = 600,
        )
        return debateRepository.findById(response.id).orElseThrow()
    }

    private fun createNotification(
        recipient: User,
        debate: Debate,
        type: NotificationType = NotificationType.QUEUE_REQUEST_ADDED,
        requesterNickname: String? = "참가신청게스트",
    ) = notificationRepository.save(
        Notification.create(
            recipient = recipient,
            debate = debate,
            type = type,
            message = NotificationMessages.build(
                type = type,
                debate = debate,
                requesterNickname = requesterNickname,
            ),
        ),
    )

    @Test
    fun `호스트 입장에서 알림 목록을 조회한다`() {
        val email = "notification-list-host@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "알림조회호스트",
        )
        val me = userRepository.findByEmail(email = email)!!
        val debate = createDebate(
            hostEmail = email,
            title = "내가 개설한 토론",
        )
        createNotification(
            recipient = me,
            debate = debate,
            type = NotificationType.QUEUE_REQUEST_ADDED,
            requesterNickname = "참가신청게스트",
        )
        createNotification(
            recipient = me,
            debate = debate,
            type = NotificationType.QUEUE_REQUEST_REMOVED,
            requesterNickname = "참가신청게스트",
        )

        restDocs(mockMvc, "notifications/get-my-list-host") {
            summary("내 알림 목록 조회")
            tag("Notifications")
            request {
                get("/api/v1/notifications")
                header("Authorization", "Bearer $accessToken")
                queryParameters {
                    param("cursor", null, "이전 응답의 nextCursor").optional()
                    param("pageSize", "10", "페이지 크기 (기본 20, 최대 50)").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 알림 목록") {
                        array("items", "알림 목록 (호스트 입장: 참가 신청 접수·취소)") {
                            field("notificationId", "알림 ID")
                            field("debateId", "관련 토론방 ID")
                            field("type", "알림 종류")
                            field("message", "알림 메시지")
                            field("isRead", "읽음 여부")
                            field("createdAt", "알림 생성 시각")
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
    fun `게스트 입장에서 알림 목록을 조회한다`() {
        val email = "notification-list-guest@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "알림조회게스트",
        )
        val me = userRepository.findByEmail(email = email)!!

        val acceptedHostEmail = "notification-list-accepted-host@pangyeori.com"
        issueAccessToken(
            email = acceptedHostEmail,
            nickname = "수락토론방장",
        )
        val acceptedDebate = createDebate(
            hostEmail = acceptedHostEmail,
            title = "내가 참가 확정된 토론",
        )
        createNotification(
            recipient = me,
            debate = acceptedDebate,
            type = NotificationType.QUEUE_REQUEST_ACCEPTED,
        )

        val rejectedHostEmail = "notification-list-rejected-host@pangyeori.com"
        issueAccessToken(
            email = rejectedHostEmail,
            nickname = "거절토론방장",
        )
        val rejectedDebate = createDebate(
            hostEmail = rejectedHostEmail,
            title = "내가 참가 거절된 토론",
        )
        createNotification(
            recipient = me,
            debate = rejectedDebate,
            type = NotificationType.QUEUE_REQUEST_REJECTED,
        )

        val cancelledHostEmail = "notification-list-cancelled-host@pangyeori.com"
        issueAccessToken(
            email = cancelledHostEmail,
            nickname = "취소토론방장",
        )
        val cancelledDebate = createDebate(
            hostEmail = cancelledHostEmail,
            title = "취소된 토론",
        )
        createNotification(
            recipient = me,
            debate = cancelledDebate,
            type = NotificationType.DEBATE_CANCELLED,
        )

        restDocs(mockMvc, "notifications/get-my-list-guest") {
            summary("내 알림 목록 조회")
            tag("Notifications")
            request {
                get("/api/v1/notifications")
                header("Authorization", "Bearer $accessToken")
                queryParameters {
                    param("cursor", null, "이전 응답의 nextCursor").optional()
                    param("pageSize", "10", "페이지 크기 (기본 20, 최대 50)").optional()
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "내 알림 목록") {
                        array("items", "알림 목록 (게스트 입장: 참가 확정·거절, 토론 취소)") {
                            field("notificationId", "알림 ID")
                            field("debateId", "관련 토론방 ID")
                            field("type", "알림 종류")
                            field("message", "알림 메시지")
                            field("isRead", "읽음 여부")
                            field("createdAt", "알림 생성 시각")
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
    fun `읽지 않은 알림 개수를 조회한다`() {
        val email = "notification-unread-count@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "미읽음조회자",
        )
        val me = userRepository.findByEmail(email = email)!!
        val debate = createDebate(
            hostEmail = email,
            title = "미읽음 개수 조회 토론",
        )
        createNotification(
            recipient = me,
            debate = debate,
        )
        createNotification(
            recipient = me,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/get-unread-count") {
            summary("읽지 않은 알림 개수 조회")
            tag("Notifications")
            request {
                get("/api/v1/notifications/unread-count")
                header("Authorization", "Bearer $accessToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "읽지 않은 알림 개수") {
                        field("count", "읽지 않은 알림 개수")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `알림을 읽음 처리한다`() {
        val email = "notification-read@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "읽음처리자",
        )
        val me = userRepository.findByEmail(email = email)!!
        val debate = createDebate(
            hostEmail = email,
            title = "알림 읽음 처리 토론",
        )
        val notification = createNotification(
            recipient = me,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/read") {
            summary("알림 읽음 처리")
            tag("Notifications")
            request {
                patch("/api/v1/notifications/{notificationId}/read")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("notificationId", notification.id!!, "알림 ID")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `본인 알림이 아니면 읽음 처리 시 404를 반환한다`() {
        val ownerEmail = "notification-read-owner@pangyeori.com"
        issueAccessToken(
            email = ownerEmail,
            nickname = "알림소유자",
        )
        val strangerToken = issueAccessToken(
            email = "notification-read-stranger@pangyeori.com",
            nickname = "알림외부인",
        )
        val owner = userRepository.findByEmail(email = ownerEmail)!!
        val debate = createDebate(
            hostEmail = ownerEmail,
            title = "알림 권한 검증 토론",
        )
        val notification = createNotification(
            recipient = owner,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/read-not-found") {
            summary("알림 읽음 처리")
            tag("Notifications")
            request {
                patch("/api/v1/notifications/{notificationId}/read")
                header("Authorization", "Bearer $strangerToken")
                pathParameters {
                    param("notificationId", notification.id!!, "알림 ID")
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
    fun `모든 알림을 읽음 처리한다`() {
        val email = "notification-read-all@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "전체읽음처리자",
        )
        val me = userRepository.findByEmail(email = email)!!
        val debate = createDebate(
            hostEmail = email,
            title = "전체 읽음 처리 토론",
        )
        createNotification(
            recipient = me,
            debate = debate,
        )
        createNotification(
            recipient = me,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/read-all") {
            summary("모든 알림 읽음 처리")
            tag("Notifications")
            request {
                patch("/api/v1/notifications/read-all")
                header("Authorization", "Bearer $accessToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `알림을 삭제한다`() {
        val email = "notification-delete@pangyeori.com"
        val accessToken = issueAccessToken(
            email = email,
            nickname = "알림삭제자",
        )
        val me = userRepository.findByEmail(email = email)!!
        val debate = createDebate(
            hostEmail = email,
            title = "알림 삭제 토론",
        )
        val notification = createNotification(
            recipient = me,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/delete") {
            summary("알림 삭제")
            tag("Notifications")
            request {
                delete("/api/v1/notifications/{notificationId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("notificationId", notification.id!!, "알림 ID")
                }
            }
            response {
                status(204)
            }
        }
    }

    @Test
    fun `본인 알림이 아니면 삭제 시 404를 반환한다`() {
        val ownerEmail = "notification-delete-owner@pangyeori.com"
        issueAccessToken(
            email = ownerEmail,
            nickname = "알림삭제소유자",
        )
        val strangerToken = issueAccessToken(
            email = "notification-delete-stranger@pangyeori.com",
            nickname = "알림삭제외부인",
        )
        val owner = userRepository.findByEmail(email = ownerEmail)!!
        val debate = createDebate(
            hostEmail = ownerEmail,
            title = "알림 삭제 권한 검증 토론",
        )
        val notification = createNotification(
            recipient = owner,
            debate = debate,
        )

        restDocs(mockMvc, "notifications/delete-not-found") {
            summary("알림 삭제")
            tag("Notifications")
            request {
                delete("/api/v1/notifications/{notificationId}")
                header("Authorization", "Bearer $strangerToken")
                pathParameters {
                    param("notificationId", notification.id!!, "알림 ID")
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
}
