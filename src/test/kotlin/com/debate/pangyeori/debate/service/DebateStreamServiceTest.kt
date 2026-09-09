package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.dto.response.DebateStatusResponse
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateStreamTicketInvalidException
import com.debate.pangyeori.debate.repository.DebateSseTicketRedisRepository
import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class DebateStreamServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val debateParticipationService = mockk<DebateParticipationService>()
    val debateSseTicketRedisRepository = mockk<DebateSseTicketRedisRepository>()
    val debateSseRegistry = mockk<DebateSseRegistry>()
    val service = DebateStreamService(
        userRepository = userRepository,
        debateParticipationService = debateParticipationService,
        debateSseTicketRedisRepository = debateSseTicketRedisRepository,
        debateSseRegistry = debateSseRegistry,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    beforeEach {
        clearMocks(
            userRepository,
            debateParticipationService,
            debateSseTicketRedisRepository,
            debateSseRegistry,
        )
    }

    Given("스트림 티켓 발급을 요청하면") {
        val debateId = "0000000000001"
        val user = fixtureMonkey.giveMeKotlinBuilder<User>()
            .set(User::id, "0000000000002")
            .set(User::email, "stream-user@pangyeori.com")
            .sample()

        When("개설자이거나 참여 요청을 한 게스트이면") {
            Then("일회성 티켓을 저장하고 만료 시간을 반환한다") {
                every { userRepository.findByEmail(email = user.email) } returns user
                every {
                    debateParticipationService.getMemberRole(
                        debateId = debateId,
                        userId = user.id!!,
                    )
                } returns DebateUserRole.GUEST
                every {
                    debateSseTicketRedisRepository.save(
                        ticket = any(),
                        debateId = debateId,
                        userId = user.id!!,
                    )
                } just runs

                val response = service.issueTicket(
                    debateId = debateId,
                    userEmail = user.email,
                )

                response.ticket.isNotBlank() shouldBe true
                response.expiresInSeconds shouldBe DebateSseTicketRedisRepository.TICKET_TTL.seconds
                verify(exactly = 1) {
                    debateSseTicketRedisRepository.save(
                        ticket = any(),
                        debateId = debateId,
                        userId = user.id!!,
                    )
                }
            }
        }

        When("참여 이력이 없으면") {
            Then("DebateAccessDeniedException을 던지고 티켓을 저장하지 않는다") {
                every { userRepository.findByEmail(email = user.email) } returns user
                every {
                    debateParticipationService.getMemberRole(
                        debateId = debateId,
                        userId = user.id!!,
                    )
                } throws DebateAccessDeniedException()

                shouldThrow<DebateAccessDeniedException> {
                    service.issueTicket(
                        debateId = debateId,
                        userEmail = user.email,
                    )
                }
                verify(exactly = 0) {
                    debateSseTicketRedisRepository.save(any(), any(), any())
                }
            }
        }

        When("사용자를 찾을 수 없으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(email = user.email) } returns null

                shouldThrow<UserNotFoundException> {
                    service.issueTicket(
                        debateId = debateId,
                        userEmail = user.email,
                    )
                }
            }
        }
    }

    Given("스트림 구독을 요청하면") {
        val debateId = "0000000000010"
        val userId = "0000000000011"
        val subscriptionId = "0000000000012"

        When("티켓이 유효하고 참여 이력이 있으면") {
            Then("스냅샷을 전송하고 emitter를 반환한다") {
                val snapshot = DebateParticipationService.StreamSnapshot(
                    role = DebateUserRole.GUEST,
                    status = DebateStatusResponse(
                        debateStatus = DebateStatus.WAITING,
                        guestStatus = DebateUserStatus.PENDING,
                        requestList = null,
                    ),
                )
                every {
                    debateSseTicketRedisRepository.consume(ticket = "valid-ticket")
                } returns DebateSseTicketRedisRepository.TicketPayload(
                    debateId = debateId,
                    userId = userId,
                )
                every {
                    debateParticipationService.getStreamSnapshot(
                        debateId = debateId,
                        userId = userId,
                    )
                } returns snapshot
                every {
                    debateSseRegistry.register(
                        debateId = debateId,
                        userId = userId,
                        role = DebateUserRole.GUEST,
                        emitter = any(),
                    )
                } returns subscriptionId
                every {
                    debateSseRegistry.sendSnapshot(
                        debateId = debateId,
                        subscriptionId = subscriptionId,
                        payload = snapshot.status,
                    )
                } returns true

                val emitter = service.subscribe(
                    debateId = debateId,
                    ticket = "valid-ticket",
                )

                emitter.timeout shouldBe 10L * 60 * 1000
                verify(exactly = 1) {
                    debateSseRegistry.sendSnapshot(
                        debateId = debateId,
                        subscriptionId = subscriptionId,
                        payload = snapshot.status,
                    )
                }
            }
        }

        When("티켓은 유효하지만 참여 이력이 없으면") {
            Then("DebateAccessDeniedException을 전파하고 구독하지 않는다") {
                every {
                    debateSseTicketRedisRepository.consume(ticket = "no-member-ticket")
                } returns DebateSseTicketRedisRepository.TicketPayload(
                    debateId = debateId,
                    userId = userId,
                )
                every {
                    debateParticipationService.getStreamSnapshot(
                        debateId = debateId,
                        userId = userId,
                    )
                } throws DebateAccessDeniedException()

                shouldThrow<DebateAccessDeniedException> {
                    service.subscribe(
                        debateId = debateId,
                        ticket = "no-member-ticket",
                    )
                }
                verify(exactly = 0) {
                    debateSseRegistry.register(any(), any(), any(), any())
                }
            }
        }

        When("티켓이 없거나 만료되었으면") {
            Then("DebateStreamTicketInvalidException을 던진다") {
                every { debateSseTicketRedisRepository.consume(ticket = "missing") } returns null

                shouldThrow<DebateStreamTicketInvalidException> {
                    service.subscribe(
                        debateId = debateId,
                        ticket = "missing",
                    )
                }
            }
        }

        When("티켓의 토론방이 경로와 다르면") {
            Then("DebateStreamTicketInvalidException을 던진다") {
                every {
                    debateSseTicketRedisRepository.consume(ticket = "mismatch")
                } returns DebateSseTicketRedisRepository.TicketPayload(
                    debateId = "9999999999999",
                    userId = userId,
                )

                shouldThrow<DebateStreamTicketInvalidException> {
                    service.subscribe(
                        debateId = debateId,
                        ticket = "mismatch",
                    )
                }
            }
        }
    }
})
