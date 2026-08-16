package com.debate.pangyeori.user.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class EmailVerificationRedisRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun saveCode(
        email: String,
        code: String,
    ) {
        redisTemplate.opsForValue().set(codeKey(email), code, CODE_TTL)
    }

    fun trySaveRateLimit(
        email: String,
    ): Boolean = redisTemplate.opsForValue().setIfAbsent(rateLimitKey(email), RATE_LIMIT_VALUE, RATE_LIMIT_TTL)

    fun incrementAttempt(
        email: String,
    ): Long {
        val key = attemptKey(email)
        val count = redisTemplate.opsForValue().increment(key)
        if (count == 1L) {
            redisTemplate.expire(key, CODE_TTL)
        }
        return count
    }

    fun resetAttempt(
        email: String,
    ) {
        redisTemplate.delete(attemptKey(email))
    }

    fun findCode(
        email: String,
    ): String? = redisTemplate.opsForValue().get(codeKey(email))

    fun deleteCode(
        email: String,
    ) {
        redisTemplate.delete(codeKey(email))
    }

    fun markVerified(
        email: String,
    ) {
        redisTemplate.opsForValue().set(verifiedKey(email), VERIFIED_VALUE, VERIFIED_TTL)
    }

    fun isVerified(
        email: String,
    ): Boolean = redisTemplate.hasKey(verifiedKey(email))

    fun clearVerified(
        email: String,
    ) {
        redisTemplate.delete(verifiedKey(email))
    }

    private fun codeKey(
        email: String,
    ) = "$CODE_KEY_PREFIX$email"

    private fun verifiedKey(
        email: String,
    ) = "$VERIFIED_KEY_PREFIX$email"

    private fun rateLimitKey(
        email: String,
    ) = "$RATE_LIMIT_KEY_PREFIX$email"

    private fun attemptKey(
        email: String,
    ) = "$ATTEMPT_KEY_PREFIX$email"

    companion object {
        private const val CODE_KEY_PREFIX = "email-verification:code:"
        private const val VERIFIED_KEY_PREFIX = "email-verification:verified:"
        private const val RATE_LIMIT_KEY_PREFIX = "email-verification:rate-limit:"
        private const val ATTEMPT_KEY_PREFIX = "email-verification:attempt:"
        private const val VERIFIED_VALUE = "true"
        private const val RATE_LIMIT_VALUE = "true"
        private val CODE_TTL = Duration.ofMinutes(5)
        private val VERIFIED_TTL = Duration.ofMinutes(30)
        private val RATE_LIMIT_TTL = Duration.ofSeconds(60)
    }
}
