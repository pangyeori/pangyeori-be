package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndAlreadyProposedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndNotProposedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndSelfResponseNotAllowedException
import com.debate.pangyeori.debate.exception.DebateNotFreeStageException
import com.debate.pangyeori.debate.repository.DebateEarlyEndRedisRepository
import com.debate.pangyeori.debate.repository.DebateEarlyEndRedisRepository.Proposal
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
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
import io.mockk.mockk
import io.mockk.verify
import org.springframework.scheduling.TaskScheduler
import java.util.Optional

class DebateEarlyEndServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val userRepository = mockk<UserRepository>()
    val debateEarlyEndRedisRepository = mockk<DebateEarlyEndRedisRepository>(relaxed = true)
    val debateFreeMessageService = mockk<DebateFreeMessageService>(relaxed = true)
    val debateConnectionEventPublisher = mockk<RedisDebateConnectionEventPublisher>(relaxed = true)
    val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    val service = DebateEarlyEndService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        userRepository = userRepository,
        debateEarlyEndRedisRepository = debateEarlyEndRedisRepository,
        debateFreeMessageService = debateFreeMessageService,
        debateConnectionEventPublisher = debateConnectionEventPublisher,
        taskScheduler = taskScheduler,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val debateId = "0000000000001"
    val hostId = "0000000000002"
    val guestId = "0000000000003"
    val hostEmail = "host@pangyeori.com"

    beforeEach {
        clearMocks(
            debateRepository,
            debateUserRepository,
            userRepository,
            debateEarlyEndRedisRepository,
            debateFreeMessageService,
            debateConnectionEventPublisher,
            taskScheduler,
        )
    }

    fun debate(
        stage: DebateStage = DebateStage.FREE,
    ) = fixtureMonkey.giveMeKotlinBuilder<Debate>()
        .set(Debate::id, debateId)
        .set(Debate::status, DebateStatus.IN_PROGRESS)
        .set(Debate::currentStage, stage)
        .sample()

    fun member(
        role: DebateUserRole,
        userId: String = hostId,
    ) = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
        .set(DebateUser::user, fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, userId).sample())
        .set(DebateUser::role, role)
        .sample()

    Given("조기 종료 상태를 조회하면") {
        When("사용자를 찾을 수 없으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(hostEmail) } returns null

                shouldThrow<UserNotFoundException> {
                    service.getStatus(debateId = debateId, userEmail = hostEmail)
                }
            }
        }

        When("토론방 멤버가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.getStatus(debateId = debateId, userEmail = hostEmail)
                }
            }
        }

        When("진행 중인 제안이 있으면") {
            Then("pending과 제안자 역할을 응답한다") {
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = guestId, proposerRole = DebateUserRole.GUEST)

                val response = service.getStatus(debateId = debateId, userEmail = hostEmail)

                response.pending shouldBe true
                response.proposerRole shouldBe DebateUserRole.GUEST
            }
        }

        When("진행 중인 제안이 없으면") {
            Then("pending을 false로 응답한다") {
                every { userRepository.findByEmail(hostEmail) } returns
                    fixtureMonkey.giveMeKotlinBuilder<User>().set(User::id, hostId).sample()
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.find(debateId) } returns null

                val response = service.getStatus(debateId = debateId, userEmail = hostEmail)

                response.pending shouldBe false
                response.proposerRole shouldBe null
            }
        }
    }

    Given("조기 종료를 제안하면") {
        When("자유 토론 단계가 아니면") {
            Then("DebateNotFreeStageException을 던진다") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate(stage = DebateStage.VERDICT_2))

                shouldThrow<DebateNotFreeStageException> {
                    service.propose(debateId = debateId, userId = hostId)
                }
            }
        }

        When("토론방 멤버가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate())
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.propose(debateId = debateId, userId = hostId)
                }
            }
        }

        When("이미 진행 중인 제안이 있으면") {
            Then("DebateEarlyEndAlreadyProposedException을 던진다") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate())
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.propose(debateId, hostId, DebateUserRole.HOST) } returns false

                shouldThrow<DebateEarlyEndAlreadyProposedException> {
                    service.propose(debateId = debateId, userId = hostId)
                }
            }
        }

        When("정상적으로 제안하면") {
            Then("실시간 이벤트를 발행하고 만료 타이머를 예약한다") {
                every { debateRepository.findById(debateId) } returns Optional.of(debate())
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.propose(debateId, hostId, DebateUserRole.HOST) } returns true

                service.propose(debateId = debateId, userId = hostId)

                verify(exactly = 1) {
                    debateConnectionEventPublisher.publish(
                        match<DebateConnectionEvent> {
                            it.type == DebateConnectionEvent.Type.EARLY_END_PROPOSED && it.role == DebateUserRole.HOST
                        },
                    )
                }
                verify(exactly = 1) { taskScheduler.schedule(any<Runnable>(), any<java.time.Instant>()) }
            }
        }
    }

    Given("조기 종료 제안을 수락하면") {
        When("토론방 멤버가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns null

                shouldThrow<DebateAccessDeniedException> {
                    service.accept(debateId = debateId, userId = guestId)
                }
            }
        }

        When("진행 중인 제안이 없으면") {
            Then("DebateEarlyEndNotProposedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns
                    member(DebateUserRole.GUEST, guestId)
                every { debateEarlyEndRedisRepository.find(debateId) } returns null

                shouldThrow<DebateEarlyEndNotProposedException> {
                    service.accept(debateId = debateId, userId = guestId)
                }
            }
        }

        When("본인이 제안한 것이면") {
            Then("DebateEarlyEndSelfResponseNotAllowedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = hostId, proposerRole = DebateUserRole.HOST)

                shouldThrow<DebateEarlyEndSelfResponseNotAllowedException> {
                    service.accept(debateId = debateId, userId = hostId)
                }
            }
        }

        When("상대방의 제안을 수락하면") {
            Then("제안을 지우고 조기 종료를 진행한다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns
                    member(DebateUserRole.GUEST, guestId)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = hostId, proposerRole = DebateUserRole.HOST)

                service.accept(debateId = debateId, userId = guestId)

                verify(exactly = 1) { debateEarlyEndRedisRepository.clear(debateId) }
                verify(exactly = 1) {
                    debateConnectionEventPublisher.publish(
                        match<DebateConnectionEvent> { it.type == DebateConnectionEvent.Type.EARLY_END_ACCEPTED },
                    )
                }
                verify(exactly = 1) { debateFreeMessageService.endByAgreement(debateId) }
            }
        }
    }

    Given("조기 종료 제안을 거절하면") {
        When("상대방의 제안을 거절하면") {
            Then("제안을 지우고 조기 종료를 진행하지 않는다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns
                    member(DebateUserRole.GUEST, guestId)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = hostId, proposerRole = DebateUserRole.HOST)

                service.decline(debateId = debateId, userId = guestId)

                verify(exactly = 1) { debateEarlyEndRedisRepository.clear(debateId) }
                verify(exactly = 1) {
                    debateConnectionEventPublisher.publish(
                        match<DebateConnectionEvent> { it.type == DebateConnectionEvent.Type.EARLY_END_DECLINED },
                    )
                }
                verify(exactly = 0) { debateFreeMessageService.endByAgreement(any()) }
            }
        }
    }

    Given("조기 종료 제안을 취소하면") {
        When("제안자가 아니면") {
            Then("DebateAccessDeniedException을 던진다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, guestId) } returns
                    member(DebateUserRole.GUEST, guestId)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = hostId, proposerRole = DebateUserRole.HOST)

                shouldThrow<DebateAccessDeniedException> {
                    service.cancel(debateId = debateId, userId = guestId)
                }
            }
        }

        When("제안자 본인이면") {
            Then("제안을 지운다") {
                every { debateUserRepository.findByDebateIdAndUserId(debateId, hostId) } returns
                    member(DebateUserRole.HOST)
                every { debateEarlyEndRedisRepository.find(debateId) } returns
                    Proposal(proposerId = hostId, proposerRole = DebateUserRole.HOST)

                service.cancel(debateId = debateId, userId = hostId)

                verify(exactly = 1) { debateEarlyEndRedisRepository.clear(debateId) }
                verify(exactly = 1) {
                    debateConnectionEventPublisher.publish(
                        match<DebateConnectionEvent> { it.type == DebateConnectionEvent.Type.EARLY_END_CANCELLED },
                    )
                }
            }
        }
    }
})
