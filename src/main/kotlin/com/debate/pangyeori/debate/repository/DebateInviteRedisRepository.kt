package com.debate.pangyeori.debate.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Repository
class DebateInviteRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun save(
        token: String,
        debateId: String,
    ) {
        val value = objectMapper.writeValueAsString(
            InviteTokenValue(
                debateId = debateId,
            ),
        )
        redisTemplate.opsForValue().set(tokenKey(token), value, INVITE_TOKEN_TTL)
    }

    fun findDebateId(
        token: String,
    ): String? {
        val value = redisTemplate.opsForValue().get(tokenKey(token)) ?: return null

        return objectMapper.readValue(value, InviteTokenValue::class.java).debateId
    }

    private fun tokenKey(
        token: String,
    ) = "$TOKEN_KEY_PREFIX$token"

    private data class InviteTokenValue(
        val debateId: String,
    )

    companion object {
        private const val TOKEN_KEY_PREFIX = "invite:token:"
        private val INVITE_TOKEN_TTL = Duration.ofHours(24)
    }
}
