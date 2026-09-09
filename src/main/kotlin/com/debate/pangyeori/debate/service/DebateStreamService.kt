package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.dto.response.DebateStreamTicketResponse
import com.debate.pangyeori.debate.exception.DebateStreamTicketInvalidException
import com.debate.pangyeori.debate.repository.DebateSseTicketRedisRepository
import com.debate.pangyeori.debate.stream.registry.DebateSseRegistry
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.security.SecureRandom
import java.util.*

@Service
class DebateStreamService(
    private val userRepository: UserRepository,
    private val debateParticipationService: DebateParticipationService,
    private val debateSseTicketRedisRepository: DebateSseTicketRedisRepository,
    private val debateSseRegistry: DebateSseRegistry,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun issueTicket(
        debateId: String,
        userEmail: String,
    ): DebateStreamTicketResponse {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val userId = user.id!!
        // 개설자이거나 참여 요청을 한 게스트만 구독 가능
        debateParticipationService.getMemberRole(
            debateId = debateId,
            userId = userId,
        )

        val ticket = generateTicket()
        debateSseTicketRedisRepository.save(
            ticket = ticket,
            debateId = debateId,
            userId = userId,
        )
        logger.info { "토론 상태 스트림 티켓을 발급했습니다. debateId=$debateId, userId=$userId" }

        return DebateStreamTicketResponse(
            ticket = ticket,
            expiresInSeconds = DebateSseTicketRedisRepository.TICKET_TTL.seconds,
        )
    }

    fun subscribe(
        debateId: String,
        ticket: String,
    ): SseEmitter {
        val ticketPayload = debateSseTicketRedisRepository.consume(
            ticket = ticket,
        ) ?: throw DebateStreamTicketInvalidException()
        if (ticketPayload.debateId != debateId) {
            throw DebateStreamTicketInvalidException()
        }

        val snapshot = debateParticipationService.getStreamSnapshot(
            debateId = debateId,
            userId = ticketPayload.userId,
        )

        val emitter = SseEmitter(EMITTER_TIMEOUT_MILLIS)
        val subscriptionId = debateSseRegistry.register(
            debateId = debateId,
            userId = ticketPayload.userId,
            role = snapshot.role,
            emitter = emitter,
        )
        emitter.onCompletion {
            debateSseRegistry.remove(
                debateId = debateId,
                subscriptionId = subscriptionId,
            )
        }
        emitter.onTimeout {
            debateSseRegistry.remove(
                debateId = debateId,
                subscriptionId = subscriptionId,
            )
            emitter.complete()
        }
        emitter.onError {
            debateSseRegistry.remove(
                debateId = debateId,
                subscriptionId = subscriptionId,
            )
        }

        debateSseRegistry.sendSnapshot(
            debateId = debateId,
            subscriptionId = subscriptionId,
            payload = snapshot.status,
        )

        return emitter
    }

    private fun generateTicket(): String {
        val bytes = ByteArray(TICKET_BYTE_LENGTH)
        SECURE_RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val TICKET_BYTE_LENGTH = 32
        private const val EMITTER_TIMEOUT_MILLIS = 10L * 60 * 1000
        private val SECURE_RANDOM = SecureRandom()
    }
}
