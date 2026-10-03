package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class DebatePresenceRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(
        debateId: String,
        sessionId: String,
        member: Member,
    ) {
        val presenceKey = presenceKey(debateId)
        redisTemplate.opsForHash<String, String>().put(presenceKey, sessionId, encode(member))
        redisTemplate.expire(presenceKey, PRESENCE_TTL)
        redisTemplate.opsForValue().set(sessionKey(sessionId), debateId, PRESENCE_TTL)
    }

    fun find(
        debateId: String,
        sessionId: String,
    ): Member? = redisTemplate.opsForHash<String, String>()
        .get(presenceKey(debateId), sessionId)
        ?.let { decode(it) }

    fun findDebateIdBySession(
        sessionId: String,
    ): String? = redisTemplate.opsForValue().get(sessionKey(sessionId))

    fun findMembers(
        debateId: String,
    ): List<Member> = redisTemplate.opsForHash<String, String>()
        .values(presenceKey(debateId))
        .map { decode(it) }

    fun remove(
        debateId: String,
        sessionId: String,
    ) {
        redisTemplate.opsForHash<String, String>().delete(presenceKey(debateId), sessionId)
        redisTemplate.delete(sessionKey(sessionId))
    }

    private fun encode(
        member: Member,
    ) = "${member.userId}$DELIMITER${member.role.code}"

    private fun decode(
        value: String,
    ): Member {
        val (userId, role) = value.split(DELIMITER, limit = 2)
        return Member(
            userId = userId,
            role = DebateUserRole.fromCode(role),
        )
    }

    private fun presenceKey(
        debateId: String,
    ) = "$KEY_PREFIX$debateId:presence"

    private fun sessionKey(
        sessionId: String,
    ) = "$SESSION_KEY_PREFIX$sessionId"

    data class Member(
        val userId: String,
        val role: DebateUserRole,
    )

    companion object {
        private const val KEY_PREFIX = "debate:"
        private const val SESSION_KEY_PREFIX = "debate:ws:session:"
        private const val DELIMITER = ":"
        private val PRESENCE_TTL = Duration.ofHours(6)
    }
}
