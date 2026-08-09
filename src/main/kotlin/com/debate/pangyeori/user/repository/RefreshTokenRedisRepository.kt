package com.debate.pangyeori.user.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Repository
class RefreshTokenRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(token: String, email: String, expiresInSeconds: Long) {
        redisTemplate.opsForValue().set(key(token), email, Duration.ofSeconds(expiresInSeconds))
    }

    fun consume(token: String, email: String): Boolean =
        redisTemplate.opsForValue().getAndDelete(key(token)) == email

    fun delete(token: String) {
        redisTemplate.delete(key(token))
    }

    private fun key(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$KEY_PREFIX$digest"
    }

    companion object {
        private const val KEY_PREFIX = "auth:refresh-token:"
    }
}
