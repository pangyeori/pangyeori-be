package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateCreatedEvent
import com.debate.pangyeori.debate.exception.InvalidFreeDebateTimeException
import com.debate.pangyeori.debate.exception.InvalidPositionException
import com.debate.pangyeori.debate.exception.InvalidTurnTimeException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher

class DebateServiceTest : BehaviorSpec({
    val debateRepository = mockk<DebateRepository>()
    val debateUserRepository = mockk<DebateUserRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val debateService = DebateService(
        debateRepository = debateRepository,
        debateUserRepository = debateUserRepository,
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
            userRepository,
            eventPublisher,
        )
    }

    Given("로그인한 사용자가 토론방 생성을 요청하면") {
        When("사용자가 존재하면") {
            Then("WAITING 토론방과 ACCEPTED HOST 참여자를 저장하고 초대 토큰 이벤트를 발행한다") {
                val email = "host@pangyeori.com"
                val host = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000001")
                    .set(User::email, email)
                    .set(User::nickname, "방장")
                    .sample()
                val savedDebate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, "0000000000002")
                    .set(Debate::host, host)
                    .set(Debate::title, "AI는 인간을 대체할 것인가")
                    .set(Debate::description, "토론 설명")
                    .set(Debate::hostPosition, DebatePosition.PROS)
                    .set(Debate::status, DebateStatus.WAITING)
                    .set(Debate::turnTimeSeconds, 180)
                    .set(Debate::freeDebateTimeSeconds, 600)
                    .set(Debate::inviteToken, "550e8400-e29b-41d4-a716-446655440000")
                    .sample()
                val savedMember = fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
                    .set(DebateUser::id, "0000000000003")
                    .set(DebateUser::debate, savedDebate)
                    .set(DebateUser::user, host)
                    .set(DebateUser::role, DebateUserRole.HOST)
                    .set(DebateUser::position, DebatePosition.PROS)
                    .set(DebateUser::status, DebateUserStatus.ACCEPTED)
                    .sample()
                val eventSlot = slot<DebateCreatedEvent>()

                every { userRepository.findByEmail(email = email) } returns host
                every { debateRepository.save(any()) } returns savedDebate
                every { debateUserRepository.save(any()) } returns savedMember
                every { eventPublisher.publishEvent(capture(eventSlot)) } just runs

                val response = debateService.create(
                    hostEmail = email,
                    title = " AI는 인간을 대체할 것인가 ",
                    description = " 토론 설명 ",
                    hostPosition = "PROS",
                    turnTimeSeconds = 180,
                    freeDebateTimeSeconds = 600,
                )

                response.id shouldBe savedDebate.id
                response.guestPosition shouldBe DebatePosition.CONS
                response.members.single().role shouldBe DebateUserRole.HOST
                eventSlot.captured.debateId shouldBe savedDebate.id
                eventSlot.captured.inviteToken.length shouldBe 36
            }
        }

        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(email = "missing@pangyeori.com") } returns null

                shouldThrow<UserNotFoundException> {
                    debateService.create(
                        hostEmail = "missing@pangyeori.com",
                        title = "토론 주제",
                        description = null,
                        hostPosition = "CONS",
                        turnTimeSeconds = 180,
                        freeDebateTimeSeconds = 600,
                    )
                }
            }
        }
    }

    Given("토론방 생성 조건을 검증할 때") {
        When("포지션이 PROS 또는 CONS가 아니면") {
            Then("InvalidPositionException을 던진다") {
                shouldThrow<InvalidPositionException> {
                    debateService.create(
                        hostEmail = "host@pangyeori.com",
                        title = "토론 주제",
                        description = null,
                        hostPosition = "INVALID",
                        turnTimeSeconds = 180,
                        freeDebateTimeSeconds = 600,
                    )
                }
            }
        }

        When("턴 시간이 허용 범위를 벗어나면") {
            Then("InvalidTurnTimeException을 던진다") {
                shouldThrow<InvalidTurnTimeException> {
                    debateService.create(
                        hostEmail = "host@pangyeori.com",
                        title = "토론 주제",
                        description = null,
                        hostPosition = "PROS",
                        turnTimeSeconds = 29,
                        freeDebateTimeSeconds = 600,
                    )
                }
            }
        }

        When("자유 토론 시간이 허용 범위를 벗어나면") {
            Then("InvalidFreeDebateTimeException을 던진다") {
                shouldThrow<InvalidFreeDebateTimeException> {
                    debateService.create(
                        hostEmail = "host@pangyeori.com",
                        title = "토론 주제",
                        description = null,
                        hostPosition = "CONS",
                        turnTimeSeconds = 180,
                        freeDebateTimeSeconds = 1801,
                    )
                }
            }
        }
    }
})
