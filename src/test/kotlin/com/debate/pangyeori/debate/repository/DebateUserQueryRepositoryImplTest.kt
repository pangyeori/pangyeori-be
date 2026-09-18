package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import com.debate.pangyeori.support.containers.TestContainersInitializer
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ContextConfiguration(initializers = [TestContainersInitializer::class])
@Transactional
class DebateUserQueryRepositoryImplTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var debateRepository: DebateRepository

    @Autowired
    private lateinit var debateUserRepository: DebateUserRepository

    @Autowired
    private lateinit var debateService: DebateService

    @Autowired
    private lateinit var debateParticipationService: DebateParticipationService

    @Autowired
    private lateinit var entityManager: EntityManager

    private fun createUser(
        email: String,
        nickname: String,
    ) = userRepository.save(
        User.create(
            email = email,
            password = "password123!",
            nickname = nickname,
        ),
    )

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

    @Test
    fun `참여를 취소하거나 거절된 토론방은 제외하고 최신 생성순으로 조회한다`() {
        val host = createUser(
            email = "query-repo-host@pangyeori.com",
            nickname = "제외조회방장",
        )
        val me = createUser(
            email = "query-repo-me@pangyeori.com",
            nickname = "제외조회자",
        )
        val other = createUser(
            email = "query-repo-other@pangyeori.com",
            nickname = "제외조회경쟁자",
        )
        val cancelled = createDebate(
            hostEmail = host.email,
            title = "취소할 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = cancelled.id,
            userEmail = me.email,
        )
        debateParticipationService.cancelParticipation(
            debateId = cancelled.id,
            userEmail = me.email,
        )
        val rejected = createDebate(
            hostEmail = host.email,
            title = "거절될 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = rejected.id,
            userEmail = me.email,
        )
        debateParticipationService.requestParticipation(
            debateId = rejected.id,
            userEmail = other.email,
        )
        debateParticipationService.acceptGuest(
            debateId = rejected.id,
            hostEmail = host.email,
            guestUserId = other.id!!,
        )
        val joined = createDebate(
            hostEmail = host.email,
            title = "참여할 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = joined.id,
            userEmail = me.email,
        )
        debateParticipationService.acceptGuest(
            debateId = joined.id,
            hostEmail = host.email,
            guestUserId = me.id!!,
        )
        val hosted = createDebate(
            hostEmail = me.email,
            title = "내가 개설한 토론",
        )

        val result = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        result.map { it.debate.id } shouldBe listOf(hosted.id, joined.id)
    }

    @Test
    fun `호스트가 아직 게스트를 선택하지 않은 PENDING 참여 요청도 조회된다`() {
        val host = createUser(
            email = "query-repo-pending-host@pangyeori.com",
            nickname = "대기조회방장",
        )
        val me = createUser(
            email = "query-repo-pending-me@pangyeori.com",
            nickname = "대기조회자",
        )
        val pending = createDebate(
            hostEmail = host.email,
            title = "아직 선택되지 않은 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = pending.id,
            userEmail = me.email,
        )

        val result = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        result.map { it.debate.id } shouldBe listOf(pending.id)
        result.single().status shouldBe DebateUserStatus.PENDING
    }

    @Test
    fun `호스트 입장에서도 아직 게스트를 선택하지 않은 토론방이 조회된다`() {
        val host = createUser(
            email = "query-repo-host-pending-host@pangyeori.com",
            nickname = "대기방장",
        )
        val guest = createUser(
            email = "query-repo-host-pending-guest@pangyeori.com",
            nickname = "대기요청자",
        )
        val debate = createDebate(
            hostEmail = host.email,
            title = "아직 게스트를 선택하지 않은 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = guest.email,
        )

        val result = debateUserRepository.findAllParticipating(
            userId = host.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        result.map { it.debate.id } shouldBe listOf(debate.id)
        result.single().role shouldBe DebateUserRole.HOST
        result.single().status shouldBe DebateUserStatus.ACCEPTED
        result.single().debate.guest shouldBe null
    }

    @Test
    fun `토론방 상태로 필터링한다`() {
        val me = createUser(
            email = "query-repo-status@pangyeori.com",
            nickname = "상태필터조회자",
        )
        createDebate(
            hostEmail = me.email,
            title = "대기 중인 토론",
        )
        val ready = createDebate(
            hostEmail = me.email,
            title = "매칭된 토론",
        )
        val debate = debateRepository.findById(ready.id).orElseThrow()
        debate.status = DebateStatus.READY
        debateRepository.saveAndFlush(debate)

        val result = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = DebateStatus.READY,
            role = null,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        result.map { it.debate.id } shouldBe listOf(ready.id)
    }

    @Test
    fun `내 역할로 필터링한다`() {
        val host = createUser(
            email = "query-repo-role-host@pangyeori.com",
            nickname = "역할필터방장",
        )
        val me = createUser(
            email = "query-repo-role-me@pangyeori.com",
            nickname = "역할필터조회자",
        )
        val hosted = createDebate(
            hostEmail = me.email,
            title = "내가 개설한 역할 토론",
        )
        val joined = createDebate(
            hostEmail = host.email,
            title = "내가 참여한 역할 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = joined.id,
            userEmail = me.email,
        )
        debateParticipationService.acceptGuest(
            debateId = joined.id,
            hostEmail = host.email,
            guestUserId = me.id!!,
        )

        val hostResult = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = DebateUserRole.HOST,
            keyword = null,
            cursor = null,
            limit = 10,
        )
        val guestResult = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = DebateUserRole.GUEST,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        hostResult.map { it.debate.id } shouldBe listOf(hosted.id)
        guestResult.map { it.debate.id } shouldBe listOf(joined.id)
    }

    @Test
    fun `제목 또는 설명에 검색어가 포함된 토론방만 조회한다`() {
        val me = createUser(
            email = "query-repo-keyword@pangyeori.com",
            nickname = "키워드검색조회자",
        )
        val titleMatched = createDebate(
            hostEmail = me.email,
            title = "환경 규제 강화 토론",
        )
        val descriptionMatched = createDebate(
            hostEmail = me.email,
            title = "정책 방향 토론",
            description = "환경 보호를 위한 정책을 다룹니다.",
        )
        createDebate(
            hostEmail = me.email,
            title = "경제 성장 토론",
            description = "성장 전략을 다룹니다.",
        )

        val result = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = "환경",
            cursor = null,
            limit = 10,
        )

        result.map { it.debate.id }.toSet() shouldBe setOf(titleMatched.id, descriptionMatched.id)
    }

    @Test
    fun `커서 기준으로 다음 페이지를 이어서 조회한다`() {
        val me = createUser(
            email = "query-repo-cursor@pangyeori.com",
            nickname = "커서조회자",
        )
        val first = createDebate(
            hostEmail = me.email,
            title = "첫 번째 커서 토론",
        )
        val second = createDebate(
            hostEmail = me.email,
            title = "두 번째 커서 토론",
        )
        val third = createDebate(
            hostEmail = me.email,
            title = "세 번째 커서 토론",
        )

        val firstPage = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = null,
            limit = 2,
        )
        val secondPage = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = firstPage.last().debate.id,
            limit = 2,
        )

        firstPage.map { it.debate.id } shouldBe listOf(third.id, second.id)
        secondPage.map { it.debate.id } shouldBe listOf(first.id)
    }

    @Test
    fun `상대방 엔티티를 fetch join으로 함께 즉시 로딩한다`() {
        val host = createUser(
            email = "query-repo-fetch-host@pangyeori.com",
            nickname = "즉시로딩방장",
        )
        val me = createUser(
            email = "query-repo-fetch-me@pangyeori.com",
            nickname = "즉시로딩게스트",
        )
        val debate = createDebate(
            hostEmail = host.email,
            title = "즉시 로딩 확인 토론",
        )
        debateParticipationService.requestParticipation(
            debateId = debate.id,
            userEmail = me.email,
        )
        debateParticipationService.acceptGuest(
            debateId = debate.id,
            hostEmail = host.email,
            guestUserId = me.id!!,
        )
        entityManager.flush()
        entityManager.clear()

        val result = debateUserRepository.findAllParticipating(
            userId = me.id!!,
            status = null,
            role = null,
            keyword = null,
            cursor = null,
            limit = 10,
        )

        val member = result.single()
        Hibernate.isInitialized(member.debate) shouldBe true
        Hibernate.isInitialized(member.debate.host) shouldBe true
        Hibernate.isInitialized(member.debate.guest) shouldBe true
        member.debate.host.nickname shouldBe host.nickname
        member.debate.guest?.nickname shouldBe me.nickname
    }
}
