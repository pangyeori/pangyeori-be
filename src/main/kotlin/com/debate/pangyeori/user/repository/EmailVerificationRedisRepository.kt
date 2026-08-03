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

    private fun codeKey(
        email: String,
    ) = "$CODE_KEY_PREFIX$email"

    private fun verifiedKey(
        email: String,
    ) = "$VERIFIED_KEY_PREFIX$email"

    companion object {
        private const val CODE_KEY_PREFIX = "email-verification:code:"
        private const val VERIFIED_KEY_PREFIX = "email-verification:verified:"
        private const val VERIFIED_VALUE = "true"
        private val CODE_TTL = Duration.ofMinutes(5)
        private val VERIFIED_TTL = Duration.ofMinutes(30)
    }
}
