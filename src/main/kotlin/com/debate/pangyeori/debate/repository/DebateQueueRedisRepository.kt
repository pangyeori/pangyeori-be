package com.debate.pangyeori.debate.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
class DebateQueueRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun add(
        debateId: String,
        userId: String,
    ) {
        redisTemplate.opsForList().rightPush(queueKey(debateId), userId)
    }

    fun remove(
        debateId: String,
        userId: String,
    ) {
        redisTemplate.opsForList().remove(queueKey(debateId), 1, userId)
    }

    fun clear(
        debateId: String,
    ) {
        redisTemplate.delete(queueKey(debateId))
    }

    fun findAll(
        debateId: String,
    ): List<String>? {
        val key = queueKey(debateId)
        if (redisTemplate.hasKey(key) != true) return null

        return redisTemplate.opsForList().range(key, 0, -1).orEmpty()
    }

    fun replace(
        debateId: String,
        userIds: List<String>,
    ) {
        val key = queueKey(debateId)
        redisTemplate.delete(key)
        if (userIds.isNotEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, userIds)
        }
    }

    private fun queueKey(
        debateId: String,
    ) = "$QUEUE_KEY_PREFIX$debateId:queue"

    companion object {
        private const val QUEUE_KEY_PREFIX = "debate:"
    }
}
