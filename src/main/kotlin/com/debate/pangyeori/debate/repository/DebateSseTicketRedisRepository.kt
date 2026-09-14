package com.debate.pangyeori.debate.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Repository
class DebateSseTicketRedisRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun save(
        ticket: String,
        debateId: String,
        userId: String,
    ) {
        val value = objectMapper.writeValueAsString(
            TicketPayload(
                debateId = debateId,
                userId = userId,
            ),
        )
        redisTemplate.opsForValue().set(ticketKey(ticket), value, TICKET_TTL)
    }

    fun consume(
        ticket: String,
    ): TicketPayload? {
        val value = redisTemplate.opsForValue().getAndDelete(ticketKey(ticket)) ?: return null

        return objectMapper.readValue(value, TicketPayload::class.java)
    }

    private fun ticketKey(
        ticket: String,
    ) = "$TICKET_KEY_PREFIX${hash(ticket)}"

    private fun hash(
        ticket: String,
    ): String = MessageDigest.getInstance(HASH_ALGORITHM)
        .digest(ticket.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    data class TicketPayload(
        val debateId: String,
        val userId: String,
    )

    companion object {
        private const val TICKET_KEY_PREFIX = "debate:sse:ticket:"
        private const val HASH_ALGORITHM = "SHA-256"
        val TICKET_TTL: Duration = Duration.ofSeconds(60)
    }
}
