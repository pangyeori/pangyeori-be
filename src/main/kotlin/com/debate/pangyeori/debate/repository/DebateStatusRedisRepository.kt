package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class DebateStatusRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(
        debateId: String,
        status: DebateStatus,
    ) {
        redisTemplate.opsForValue().set(statusKey(debateId), status.code)
    }

    fun find(
        debateId: String,
    ): DebateStatus? {
        val code = redisTemplate.opsForValue().get(statusKey(debateId)) ?: return null

        return DebateStatus.entries.firstOrNull { it.code == code }
    }

    private fun statusKey(
        debateId: String,
    ) = "$STATUS_KEY_PREFIX$debateId:status"

    companion object {
        private const val STATUS_KEY_PREFIX = "debate:"
    }
}
