package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateCreatedEvent
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.customizer.Values
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
                    hostPosition = DebatePosition.PROS,
                    turnTimeSeconds = 180,
                    freeDebateTimeSeconds = 600,
                )

                response.id shouldBe savedDebate.id
                response.guestPosition shouldBe DebatePosition.CONS
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
                        hostPosition = DebatePosition.CONS,
                        turnTimeSeconds = 180,
                        freeDebateTimeSeconds = 600,
                    )
                }
            }
        }
    }

    Given("내가 속한 토론방 목록 조회를 요청하면") {
        val email = "member@pangyeori.com"
        val me = fixtureMonkey.giveMeKotlinBuilder<User>()
            .set(User::id, "0000000000010")
            .set(User::email, email)
            .set(User::nickname, "조회자")
            .sample()
        val other = fixtureMonkey.giveMeKotlinBuilder<User>()
            .set(User::id, "0000000000011")
            .set(User::email, "other@pangyeori.com")
            .set(User::nickname, "상대방")
            .set(User::profileImageKey, "profiles/other.png")
            .sample()

        fun participation(
            debateId: String,
            role: DebateUserRole,
            host: User,
            guest: User?,
        ): DebateUser {
            val debate = setAuditFields(
                entity = fixtureMonkey.giveMeKotlinBuilder<Debate>()
                    .set(Debate::id, debateId)
                    .set(Debate::host, host)
                    .set(Debate::guest, guest)
                    .sample(),
            )

            return fixtureMonkey.giveMeKotlinBuilder<DebateUser>()
                .set(DebateUser::debate, Values.just(debate))
                .set(DebateUser::user, me)
                .set(DebateUser::role, role)
                .set(DebateUser::status, DebateUserStatus.ACCEPTED)
                .sample()
        }

        When("페이지 크기보다 많은 토론방이 조회되면") {
            Then("페이지 크기만큼만 담고 마지막 항목의 토론방 ID를 다음 커서로 반환한다") {
                val hosted = participation(
                    debateId = "0000000000023",
                    role = DebateUserRole.HOST,
                    host = me,
                    guest = other,
                )
                val joined = participation(
                    debateId = "0000000000022",
                    role = DebateUserRole.GUEST,
                    host = other,
                    guest = me,
                )
                val waiting = participation(
                    debateId = "0000000000021",
                    role = DebateUserRole.HOST,
                    host = me,
                    guest = null,
                )

                every { userRepository.findByEmail(email = email) } returns me
                every {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = null,
                        role = null,
                        keyword = null,
                        cursor = null,
                        limit = 3,
                    )
                } returns listOf(hosted, joined, waiting)

                val response = debateService.getMyDebates(
                    userEmail = email,
                    status = null,
                    role = null,
                    keyword = null,
                    cursor = null,
                    pageSize = 2,
                )

                response.items.map { it.debateId } shouldBe listOf("0000000000023", "0000000000022")
                response.hasNext shouldBe true
                response.nextCursor shouldBe "0000000000022"
                response.items[0].myRole shouldBe DebateUserRole.HOST
                response.items[0].opponent?.userId shouldBe other.id
                response.items[0].opponent?.profileImageKey shouldBe other.profileImageKey
                response.items[1].myRole shouldBe DebateUserRole.GUEST
                response.items[1].opponent?.nickname shouldBe other.nickname
            }
        }

        When("남은 토론방이 페이지 크기 이하이면") {
            Then("다음 커서 없이 반환하고 상대가 아직 없는 토론방은 opponent를 null로 내려준다") {
                val waiting = participation(
                    debateId = "0000000000021",
                    role = DebateUserRole.HOST,
                    host = me,
                    guest = null,
                )

                every { userRepository.findByEmail(email = email) } returns me
                every {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = null,
                        role = null,
                        keyword = null,
                        cursor = null,
                        limit = 3,
                    )
                } returns listOf(waiting)

                val response = debateService.getMyDebates(
                    userEmail = email,
                    status = null,
                    role = null,
                    keyword = null,
                    cursor = null,
                    pageSize = 2,
                )

                response.items.size shouldBe 1
                response.items[0].opponent shouldBe null
                response.hasNext shouldBe false
                response.nextCursor shouldBe null
            }
        }

        When("페이지 크기를 지정하지 않거나 최대값을 초과하면") {
            Then("기본값 20과 최대값 50으로 보정하고 검색어 앞뒤 공백을 제거해 조회한다") {
                every { userRepository.findByEmail(email = email) } returns me
                every {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = listOf(DebateStatus.READY),
                        role = DebateUserRole.GUEST,
                        keyword = "AI",
                        cursor = "0000000000020",
                        limit = any(),
                    )
                } returns emptyList()

                debateService.getMyDebates(
                    userEmail = email,
                    status = listOf(DebateStatus.READY),
                    role = DebateUserRole.GUEST,
                    keyword = " AI ",
                    cursor = "0000000000020",
                    pageSize = null,
                )
                debateService.getMyDebates(
                    userEmail = email,
                    status = listOf(DebateStatus.READY),
                    role = DebateUserRole.GUEST,
                    keyword = " AI ",
                    cursor = "0000000000020",
                    pageSize = 100,
                )

                verify(exactly = 1) {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = listOf(DebateStatus.READY),
                        role = DebateUserRole.GUEST,
                        keyword = "AI",
                        cursor = "0000000000020",
                        limit = 21,
                    )
                }
                verify(exactly = 1) {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = listOf(DebateStatus.READY),
                        role = DebateUserRole.GUEST,
                        keyword = "AI",
                        cursor = "0000000000020",
                        limit = 51,
                    )
                }
            }
        }

        When("검색어가 공백뿐이면") {
            Then("검색어 없이 조회한다") {
                every { userRepository.findByEmail(email = email) } returns me
                every {
                    debateUserRepository.findAllParticipating(
                        userId = me.id!!,
                        status = null,
                        role = null,
                        keyword = null,
                        cursor = null,
                        limit = 21,
                    )
                } returns emptyList()

                val response = debateService.getMyDebates(
                    userEmail = email,
                    status = null,
                    role = null,
                    keyword = "   ",
                    cursor = null,
                    pageSize = null,
                )

                response.items shouldBe emptyList()
                response.hasNext shouldBe false
            }
        }

        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(email = "missing@pangyeori.com") } returns null

                shouldThrow<UserNotFoundException> {
                    debateService.getMyDebates(
                        userEmail = "missing@pangyeori.com",
                        status = null,
                        role = null,
                        keyword = null,
                        cursor = null,
                        pageSize = null,
                    )
                }
            }
        }
    }
})
