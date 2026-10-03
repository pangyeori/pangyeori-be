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
        subscriptionId: String,
        member: Member,
    ) {
        val presenceKey = presenceKey(debateId)
        redisTemplate.opsForHash<String, String>()
            .put(presenceKey, fieldKey(sessionId, subscriptionId), encode(member))
        redisTemplate.expire(presenceKey, PRESENCE_TTL)
        redisTemplate.opsForValue().set(sessionKey(sessionId), debateId, PRESENCE_TTL)
    }

    fun findBySession(
        debateId: String,
        sessionId: String,
    ): List<Member> = redisTemplate.opsForHash<String, String>()
        .entries(presenceKey(debateId))
        .filterKeys { it.startsWith("$sessionId$FIELD_SEPARATOR") }
        .values
        .map { decode(it) }

    fun findDebateIdBySession(
        sessionId: String,
    ): String? = redisTemplate.opsForValue().get(sessionKey(sessionId))

    fun findActiveMembers(
        debateId: String,
    ): List<Member> {
        val members = redisTemplate.opsForHash<String, String>()
            .values(presenceKey(debateId))
            .map { decode(it) }
        val aliveInstances = members.map { it.instanceId }.distinct().filter { isInstanceAlive(it) }.toSet()
        return members.filter { it.instanceId in aliveInstances }
    }

    fun removeSubscription(
        debateId: String,
        sessionId: String,
        subscriptionId: String,
    ): Boolean {
        redisTemplate.opsForHash<String, String>().delete(presenceKey(debateId), fieldKey(sessionId, subscriptionId))
        val sessionLeft = findBySession(debateId, sessionId).isEmpty()
        if (sessionLeft) {
            redisTemplate.delete(sessionKey(sessionId))
        }
        return sessionLeft
    }

    fun removeSession(
        debateId: String,
        sessionId: String,
    ) {
        val presenceKey = presenceKey(debateId)
        val fields = redisTemplate.opsForHash<String, String>()
            .entries(presenceKey)
            .keys
            .filter { it.startsWith("$sessionId$FIELD_SEPARATOR") }
        if (fields.isNotEmpty()) {
            redisTemplate.opsForHash<String, String>().delete(presenceKey, *fields.toTypedArray())
        }
        redisTemplate.delete(sessionKey(sessionId))
    }

    fun refreshInstance(
        instanceId: String,
    ) {
        redisTemplate.opsForValue().set(instanceKey(instanceId), ALIVE, INSTANCE_TTL)
    }

    private fun isInstanceAlive(
        instanceId: String,
    ): Boolean = redisTemplate.hasKey(instanceKey(instanceId)) == true

    private fun encode(
        member: Member,
    ) = listOf(member.userId, member.role.code, member.instanceId).joinToString(VALUE_SEPARATOR)

    private fun decode(
        value: String,
    ): Member {
        val (userId, role, instanceId) = value.split(VALUE_SEPARATOR, limit = 3)
        return Member(
            userId = userId,
            role = DebateUserRole.fromCode(role),
            instanceId = instanceId,
        )
    }

    private fun fieldKey(
        sessionId: String,
        subscriptionId: String,
    ) = "$sessionId$FIELD_SEPARATOR$subscriptionId"

    private fun presenceKey(
        debateId: String,
    ) = "$KEY_PREFIX$debateId:presence"

    private fun sessionKey(
        sessionId: String,
    ) = "$SESSION_KEY_PREFIX$sessionId"

    private fun instanceKey(
        instanceId: String,
    ) = "$INSTANCE_KEY_PREFIX$instanceId"

    data class Member(
        val userId: String,
        val role: DebateUserRole,
        val instanceId: String,
    )

    companion object {
        private const val KEY_PREFIX = "debate:"
        private const val SESSION_KEY_PREFIX = "debate:ws:session:"
        private const val INSTANCE_KEY_PREFIX = "debate:ws:instance:"
        private const val VALUE_SEPARATOR = ":"
        private const val FIELD_SEPARATOR = "/"
        private const val ALIVE = "alive"
        private val PRESENCE_TTL = Duration.ofHours(6)
        private val INSTANCE_TTL = Duration.ofSeconds(15)
    }
}
