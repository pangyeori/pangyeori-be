package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class DebateEarlyEndRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun propose(
        debateId: String,
        proposerId: String,
        proposerRole: DebateUserRole,
    ): Boolean = redisTemplate.opsForValue().setIfAbsent(
        key(debateId),
        encode(proposerId, proposerRole),
        PROPOSAL_TTL,
    ) == true

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
    ) = listOf(proposerId, proposerRole.code).joinToString(VALUE_SEPARATOR)

    private fun decode(
        value: String,
    ): Proposal {
        val (proposerId, role) = value.split(VALUE_SEPARATOR, limit = 2)
        return Proposal(
            proposerId = proposerId,
            proposerRole = DebateUserRole.fromCode(role),
        )
    }

    private fun key(
        debateId: String,
    ) = "$KEY_PREFIX$debateId:early-end"

    data class Proposal(
        val proposerId: String,
        val proposerRole: DebateUserRole,
    )

    companion object {
        private const val KEY_PREFIX = "debate:"
        private const val VALUE_SEPARATOR = ":"
        val PROPOSAL_TTL: Duration = Duration.ofSeconds(30)
    }
}
