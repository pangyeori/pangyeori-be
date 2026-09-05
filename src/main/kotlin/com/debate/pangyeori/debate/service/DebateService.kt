package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.dto.response.DebateCreateResponse
import com.debate.pangyeori.debate.event.DebateCreatedEvent
import com.debate.pangyeori.debate.exception.InvalidFreeDebateTimeException
import com.debate.pangyeori.debate.exception.InvalidPositionException
import com.debate.pangyeori.debate.exception.InvalidTurnTimeException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DebateService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun create(
        hostEmail: String,
        title: String,
        description: String?,
        hostPosition: String?,
        turnTimeSeconds: Int,
        freeDebateTimeSeconds: Int,
    ): DebateCreateResponse {
        val validatedPosition = DebatePosition.entries.firstOrNull { it.code == hostPosition }
            ?: throw InvalidPositionException()
        if (turnTimeSeconds !in MIN_TURN_TIME_SECONDS..MAX_TURN_TIME_SECONDS) {
            throw InvalidTurnTimeException()
        }
        if (freeDebateTimeSeconds !in MIN_FREE_DEBATE_TIME_SECONDS..MAX_FREE_DEBATE_TIME_SECONDS) {
            throw InvalidFreeDebateTimeException()
        }

        val host = userRepository.findByEmail(
            email = hostEmail,
        ) ?: throw UserNotFoundException()
        val inviteToken = UUID.randomUUID().toString()
        val debate = debateRepository.save(
            Debate.create(
                host = host,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                hostPosition = validatedPosition,
                turnTimeSeconds = turnTimeSeconds,
                freeDebateTimeSeconds = freeDebateTimeSeconds,
                inviteToken = inviteToken,
            ),
        )
        val hostMember = debateUserRepository.save(
            DebateUser.create(
                debate = debate,
                user = host,
                role = DebateUserRole.HOST,
                position = validatedPosition,
            ),
        )

        eventPublisher.publishEvent(
            DebateCreatedEvent(
                debateId = debate.id!!,
                inviteToken = inviteToken,
            ),
        )

        return DebateCreateResponse.from(
            debate = debate,
            hostMember = hostMember,
        )
    }

    companion object {
        private const val MIN_TURN_TIME_SECONDS = 30
        private const val MAX_TURN_TIME_SECONDS = 600
        private const val MIN_FREE_DEBATE_TIME_SECONDS = 60
        private const val MAX_FREE_DEBATE_TIME_SECONDS = 1800
    }
}
