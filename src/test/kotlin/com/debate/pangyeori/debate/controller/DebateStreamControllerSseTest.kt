package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.dto.response.DebateStatusResponse
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.debate.service.DebateStreamService
import com.debate.pangyeori.debate.stream.message.DebateStatusChangedPayload
import com.debate.pangyeori.debate.stream.message.GuestStatusChangedPayload
import com.debate.pangyeori.debate.stream.message.QueueChangedPayload
import com.debate.pangyeori.support.asyncapi.AsyncApiDocsTest
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class DebateStreamControllerSseTest : AsyncApiDocsTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var debateService: DebateService

    @Autowired
    private lateinit var debateParticipationService: DebateParticipationService

    @Autowired
    private lateinit var debateStreamService: DebateStreamService

    @Test
    fun `개설자가 스트림을 구독하면 스냅샷과 대기열 변경 이벤트를 받는다`() {
        val host = signUp(
            email = "stream-host@pangyeori.com",
            nickname = "스트림방장",
        )
        val guest1 = signUp(
            email = "stream-guest1@pangyeori.com",
            nickname = "스트림게스트1",
        )
        val guest2 = signUp(
            email = "stream-guest2@pangyeori.com",
            nickname = "스트림게스트2",
        )
        val debate = createDebate(
            hostEmail = host.email,
            title = "SSE 문서화 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest1.email,
        )
        val ticket = debateStreamService.issueTicket(
            debateId = debate.id,
            userEmail = host.email,
        )

        documentSse("debates/status-stream") {
            channel(
                path = "/api/v1/debates/{debateId}/status/stream",
                protocol = "https",
                description = "토론 참여 상태 실시간 스트림. 발급받은 일회성 티켓을 쿼리 파라미터로 넘겨 인증한다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                pathValue("debateId", debate.id)
                query("ticket", ticket.ticket)
            }

            receive<DebateStatusResponse>(
                event = "snapshot",
                summary = "구독 직후 현재 참여 상태를 한 번 보낸다",
            ) {
                field("debateStatus", "토론방 상태")
                field("guestStatus", "요청자의 참여 상태. 개설자로 구독하면 null, 게스트로 구독하면 자신의 상태").optional()
                field("requestList", "대기 중인 참여 요청 목록. 개설자로 구독할 때만 채워지고 게스트는 null").optional()
                verify {
                    it.debateStatus shouldBe DebateStatus.WAITING
                    it.requestList!! shouldHaveSize 1
                }
            }

            receive<QueueChangedPayload>(
                event = "queue-changed",
                summary = "대기열이 바뀌면 개설자에게 갱신된 대기 목록을 보낸다",
            ) {
                field("requestList", "갱신된 대기 중인 참여 요청 목록")
                trigger {
                    debateParticipationService.requestParticipation(
                        debateId = debate.id,
                        userEmail = guest2.email,
                    )
                }
                verify {
                    it.requestList shouldHaveSize 2
                }
            }

            receive<QueueChangedPayload>(
                event = "queue-changed",
                summary = "대기열이 바뀌면 개설자에게 갱신된 대기 목록을 보낸다",
                example = "queue-cleared",
            ) {
                field("requestList", "갱신된 대기 중인 참여 요청 목록")
                trigger {
                    debateParticipationService.acceptGuest(
                        debateId = debate.id,
                        hostEmail = host.email,
                        guestUserId = guest1.id!!,
                    )
                }
                verify {
                    it.requestList.shouldBeEmpty()
                }
            }
        }
    }

    @Test
    fun `구독 중인 게스트가 선택되면 상태 변경 이벤트를 받고 스트림이 닫힌다`() {
        val host = signUp(
            email = "stream-accept-host@pangyeori.com",
            nickname = "선택방장",
        )
        val guest = signUp(
            email = "stream-accept-guest@pangyeori.com",
            nickname = "선택게스트",
        )
        val debate = createDebate(
            hostEmail = host.email,
            title = "SSE 게스트 선택 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )
        val ticket = debateStreamService.issueTicket(
            debateId = debate.id,
            userEmail = guest.email,
        )

        documentSse("debates/status-stream-guest") {
            channel(
                path = "/api/v1/debates/{debateId}/status/stream",
                protocol = "https",
                description = "토론 참여 상태 실시간 스트림. 발급받은 일회성 티켓을 쿼리 파라미터로 넘겨 인증한다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                pathValue("debateId", debate.id)
                query("ticket", ticket.ticket)
            }

            receive<DebateStatusResponse>(
                event = "snapshot",
                summary = "구독 직후 현재 참여 상태를 한 번 보낸다",
            ) {
                field("debateStatus", "토론방 상태")
                field("guestStatus", "요청자의 참여 상태. 개설자로 구독하면 null, 게스트로 구독하면 자신의 상태").optional()
                field("requestList", "대기 중인 참여 요청 목록. 개설자로 구독할 때만 채워지고 게스트는 null").optional()
                verify {
                    it.debateStatus shouldBe DebateStatus.WAITING
                    it.guestStatus shouldBe DebateUserStatus.PENDING
                    it.requestList shouldBe null
                }
            }

            receive<GuestStatusChangedPayload>(
                event = "guest-status-changed",
                summary = "본인의 참여 상태가 바뀌면 해당 게스트에게만 보낸다",
                example = "accepted",
            ) {
                field("guestStatus", "변경된 참여 상태")
                trigger {
                    debateParticipationService.acceptGuest(
                        debateId = debate.id,
                        hostEmail = host.email,
                        guestUserId = guest.id!!,
                    )
                }
                verify {
                    it.guestStatus shouldBe DebateUserStatus.ACCEPTED
                }
            }

            receive<DebateStatusChangedPayload>(
                event = "debate-status-changed",
                summary = "토론방 상태가 바뀌면 전체 구독자에게 보낸다. WAITING이 아니면 이 이벤트 뒤에 스트림이 닫힌다",
            ) {
                field("debateStatus", "변경된 토론방 상태")
                verify {
                    it.debateStatus shouldBe DebateStatus.READY
                }
            }
        }
    }

    @Test
    fun `선택되지 않은 게스트는 REJECTED 상태 변경을 받는다`() {
        val host = signUp(
            email = "stream-reject-host@pangyeori.com",
            nickname = "탈락방장",
        )
        val selectedGuest = signUp(
            email = "stream-reject-selected@pangyeori.com",
            nickname = "선택된게스트",
        )
        val rejectedGuest = signUp(
            email = "stream-reject-rejected@pangyeori.com",
            nickname = "탈락게스트",
        )
        val debate = createDebate(
            hostEmail = host.email,
            title = "SSE 탈락 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = selectedGuest.email,
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = rejectedGuest.email,
        )
        val ticket = debateStreamService.issueTicket(
            debateId = debate.id,
            userEmail = rejectedGuest.email,
        )

        documentSse("debates/status-stream-rejected") {
            channel(
                path = "/api/v1/debates/{debateId}/status/stream",
                protocol = "https",
                description = "토론 참여 상태 실시간 스트림. 발급받은 일회성 티켓을 쿼리 파라미터로 넘겨 인증한다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                pathValue("debateId", debate.id)
                query("ticket", ticket.ticket)
            }

            receive<GuestStatusChangedPayload>(
                event = "guest-status-changed",
                summary = "본인의 참여 상태가 바뀌면 해당 게스트에게만 보낸다",
                example = "rejected",
            ) {
                field("guestStatus", "변경된 참여 상태")
                trigger {
                    debateParticipationService.acceptGuest(
                        debateId = debate.id,
                        hostEmail = host.email,
                        guestUserId = selectedGuest.id!!,
                    )
                }
                verify {
                    it.guestStatus shouldBe DebateUserStatus.REJECTED
                }
            }
        }
    }

    private fun signUp(
        email: String,
        nickname: String,
    ): User = userRepository.save(
        User.create(
            email = email,
            password = passwordEncoder.encode("password123!")!!,
            nickname = nickname,
        ),
    )

    private fun createDebate(
        hostEmail: String,
        title: String,
    ) = debateService.create(
        hostEmail = hostEmail,
        title = title,
        description = null,
        hostPosition = "PROS",
        turnTimeSeconds = 180,
        freeDebateTimeSeconds = 600,
    )
}
