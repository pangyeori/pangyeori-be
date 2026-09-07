package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.*
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.AlreadyInQueueException
import com.debate.pangyeori.debate.repository.*
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class DebateParticipationServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val debateInviteRedisRepository = mockk<DebateInviteRedisRepository>()
    val debateQueueRedisRepository = mockk<DebateQueueRedisRepository>()
    val debateStatusRedisRepository = mockk<DebateStatusRedisRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val service = DebateParticipationService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
        debateInviteRedisRepository = debateInviteRedisRepository,
        debateQueueRedisRepository = debateQueueRedisRepository,
        debateStatusRedisRepository = debateStatusRedisRepository,
        userRepository = userRepository,
        eventPublisher = eventPublisher,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    beforeEach {
        clearMocks(
            debateRepository,
            debateUserRepository,
            debateInviteRedisRepository,
            debateQueueRedisRepository,
            debateStatusRedisRepository,
            userRepository,
            eventPublisher,
        )
    }

    Given("게스트가 참여 요청을 하면") {
        When("이미 PENDING 상태이면") {
            Then("AlreadyInQueueException을 던진다") {
                val host = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000001")
                    .set(User::email, "host@pangyeori.com")
                    .sample()
                val guest = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000002")
                    .set(User::email, "guest@pangyeori.com")
                    .sample()
                val debate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, "0000000000003")
                    .set(Debate::host, host)
                    .set(Debate::status, DebateStatus.WAITING)
                    .sample()
                val member = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
                    .set(DebateUser::status, DebateUserStatus.PENDING)
                    .sample()

                every { debateRepository.findWithLockById(id = debate.id!!) } returns debate
                every { userRepository.findByEmail(email = guest.email) } returns guest
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = debate.id!!,
                        userId = guest.id!!,
                    )
                } returns member

                shouldThrow<AlreadyInQueueException> {
                    service.requestParticipation(
                        debateId = debate.id!!,
                        userEmail = guest.email,
                    )
                }
            }
        }
    }

    Given("개설자가 대기 중인 게스트를 선택하면") {
        When("토론방이 WAITING 상태이면") {
            Then("선택한 게스트를 수락하고 토론방을 READY로 변경한다") {
                val host = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000011")
                    .set(User::email, "accept-host@pangyeori.com")
                    .sample()
                val guest = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000012")
                    .sample()
                val debate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, "0000000000013")
                    .set(Debate::host, host)
                    .set(Debate::status, DebateStatus.WAITING)
                    .sample()
                val selectedMember = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
                    .set(DebateUser::id, "0000000000014")
                    .set(DebateUser::user, guest)
                    .set(DebateUser::status, DebateUserStatus.PENDING)
                    .sample()

                every { debateRepository.findWithLockById(id = debate.id!!) } returns debate
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = debate.id!!,
                        userId = guest.id!!,
                    )
                } returns selectedMember
                every {
                    debateUserRepository.findAllByDebateIdAndStatus(
                        debateId = debate.id!!,
                        status = DebateUserStatus.PENDING,
                    )
                } returns listOf(selectedMember)
                every { eventPublisher.publishEvent(any<DebateQueueChangedEvent>()) } just runs
                every { eventPublisher.publishEvent(any<DebateStatusChangedEvent>()) } just runs

                val response = service.acceptGuest(
                    debateId = debate.id!!,
                    hostEmail = host.email,
                    guestUserId = guest.id!!,
                )

                response.status shouldBe DebateStatus.READY
                debate.guest shouldBe selectedMember.user
                selectedMember.status shouldBe DebateUserStatus.ACCEPTED
            }
        }
    }

    Given("개설자가 참여 상태를 조회하면") {
        When("Redis 상태와 대기열 캐시가 없으면") {
            Then("DB에서 조회하고 Redis 캐시를 복구한다") {
                val host = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000021")
                    .set(User::email, "status-host@pangyeori.com")
                    .sample()
                val debate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, "0000000000022")
                    .set(Debate::host, host)
                    .set(Debate::status, DebateStatus.WAITING)
                    .sample()
                val hostMember = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
                    .set(DebateUser::user, host)
                    .set(DebateUser::role, DebateUserRole.HOST)
                    .set(DebateUser::status, DebateUserStatus.ACCEPTED)
                    .sample()

                every { userRepository.findByEmail(email = host.email) } returns host
                every {
                    debateUserRepository.findByDebateIdAndUserId(
                        debateId = debate.id!!,
                        userId = host.id!!,
                    )
                } returns hostMember
                every { debateStatusRedisRepository.find(debateId = debate.id!!) } returns null
                every { debateRepository.findById(debate.id!!) } returns Optional.of(debate)
                every {
                    debateStatusRedisRepository.save(
                        debateId = debate.id!!,
                        status = DebateStatus.WAITING,
                    )
                } just runs
                every {
                    debateUserRepository.findAllByDebateIdAndStatusOrderByCreatedAtAsc(
                        debateId = debate.id!!,
                        status = DebateUserStatus.PENDING,
                    )
                } returns emptyList()
                every { debateQueueRedisRepository.findAll(debateId = debate.id!!) } returns null
                every {
                    debateQueueRedisRepository.replace(
                        debateId = debate.id!!,
                        userIds = emptyList(),
                    )
                } just runs

                val response = service.getStatus(
                    debateId = debate.id!!,
                    userEmail = host.email,
                )

                response.debateStatus shouldBe DebateStatus.WAITING
                response.requestList shouldBe emptyList()
                verify(exactly = 1) {
                    debateStatusRedisRepository.save(
                        debateId = debate.id!!,
                        status = DebateStatus.WAITING,
                    )
                }
                verify(exactly = 1) {
                    debateQueueRedisRepository.replace(
                        debateId = debate.id!!,
                        userIds = emptyList(),
                    )
                }
            }
        }
    }
})
