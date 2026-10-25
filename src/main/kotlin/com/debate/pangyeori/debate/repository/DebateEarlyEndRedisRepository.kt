package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class DebateEarlyEndRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun propose(
        debateId: String,
        proposerId: String,
        proposerRole: DebateUserRole,
    ): LocalDateTime? {
        val expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(PROPOSAL_TTL)
        val saved = redisTemplate.opsForValue().setIfAbsent(
            key(debateId),
            encode(proposerId, proposerRole, expiresAt),
            PROPOSAL_TTL,
        ) == true
        return if (saved) expiresAt else null
    }

    fun find(
        debateId: String,
    ): Proposal? = redisTemplate.opsForValue().get(key(debateId))?.let { decode(it) }

    fun clear(
        debateId: String,
    ) {
        redisTemplate.delete(key(debateId))
    }

    private fun encode(
        proposerId: String,
        proposerRole: DebateUserRole,
        expiresAt: LocalDateTime,
    ) = listOf(proposerId, proposerRole.code, expiresAt.toString()).joinToString(VALUE_SEPARATOR)

    private fun decode(
        value: String,
    ): Proposal {
        val (proposerId, role, expiresAt) = value.split(VALUE_SEPARATOR, limit = 3)
        return Proposal(
            proposerId = proposerId,
            proposerRole = DebateUserRole.fromCode(role),
            expiresAt = LocalDateTime.parse(expiresAt),
        )
    }

    private fun key(
        debateId: String,
    ) = "$KEY_PREFIX$debateId:early-end"

    data class Proposal(
        val proposerId: String,
        val proposerRole: DebateUserRole,
        val expiresAt: LocalDateTime,
    )

    companion object {
        private const val KEY_PREFIX = "debate:"
        private const val VALUE_SEPARATOR = ":"
        val PROPOSAL_TTL: Duration = Duration.ofSeconds(30)
    }
}
