package com.debate.pangyeori.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Repository
class PasswordResetRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun saveToken(
        email: String,
        token: String,
    ) {
        redisTemplate.opsForValue().set(tokenKey(token), email, TOKEN_TTL)
    }

    fun findEmailByToken(
        token: String,
    ): String? = redisTemplate.opsForValue().get(tokenKey(token))

    fun deleteToken(
        token: String,
    ) {
        redisTemplate.delete(tokenKey(token))
    }

    private fun tokenKey(
        token: String,
    ) = "$TOKEN_KEY_PREFIX${hash(token)}"

    private fun hash(
        token: String,
    ): String = MessageDigest.getInstance(HASH_ALGORITHM)
        .digest(token.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TOKEN_KEY_PREFIX = "password-reset:token:"
        private const val HASH_ALGORITHM = "SHA-256"
        private val TOKEN_TTL = Duration.ofMinutes(10)
    }
}
