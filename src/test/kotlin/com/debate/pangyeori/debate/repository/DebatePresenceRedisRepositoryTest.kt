package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository.Member
import com.debate.pangyeori.support.asyncapi.AsyncApiDocsTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DebatePresenceRedisRepositoryTest : AsyncApiDocsTest() {

    @Autowired
    private lateinit var presenceRepository: DebatePresenceRedisRepository

    private val debateId = "0000000000001"

    @Test
    fun `살아 있는 인스턴스의 접속 상태만 조회된다`() {
        presenceRepository.refreshInstance(ALIVE_INSTANCE)
        presenceRepository.save(
            debateId = debateId,
            sessionId = "session-alive",
            subscriptionId = "sub-0",
            member = Member("user-alive", DebateUserRole.HOST, ALIVE_INSTANCE),
        )
        presenceRepository.save(
            debateId = debateId,
            sessionId = "session-dead",
            subscriptionId = "sub-0",
            member = Member("user-dead", DebateUserRole.GUEST, DEAD_INSTANCE),
        )

        presenceRepository.findActiveMembers(debateId).shouldContainExactly(
            Member("user-alive", DebateUserRole.HOST, ALIVE_INSTANCE),
        )
    }

    @Test
    fun `모든 인스턴스가 죽었으면 접속 상태는 비어 있다`() {
        presenceRepository.save(
            debateId = debateId,
            sessionId = "session-dead",
            subscriptionId = "sub-0",
            member = Member("user-dead", DebateUserRole.HOST, DEAD_INSTANCE),
        )

        presenceRepository.findActiveMembers(debateId).shouldBeEmpty()
    }

    @Test
    fun `같은 세션의 구독이 하나 남아 있으면 세션은 이탈하지 않은 것으로 본다`() {
        presenceRepository.refreshInstance(ALIVE_INSTANCE)
        val member = Member("user-alive", DebateUserRole.HOST, ALIVE_INSTANCE)
        presenceRepository.save(debateId, "session-multi", "sub-0", member)
        presenceRepository.save(debateId, "session-multi", "sub-1", member)

        presenceRepository.removeSubscription(debateId, "session-multi", "sub-0") shouldBe false
        presenceRepository.findBySession(debateId, "session-multi").shouldContainExactly(member)

        presenceRepository.removeSubscription(debateId, "session-multi", "sub-1") shouldBe true
        presenceRepository.findBySession(debateId, "session-multi").shouldBeEmpty()
    }

    companion object {
        private const val ALIVE_INSTANCE = "instance-alive"
        private const val DEAD_INSTANCE = "instance-dead"
    }
}
