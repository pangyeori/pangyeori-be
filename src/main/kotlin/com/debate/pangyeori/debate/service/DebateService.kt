package com.debate.pangyeori.debate.service

import com.debate.pangyeori.common.dto.CursorPage
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.dto.response.DebateCreateResponse
import com.debate.pangyeori.debate.dto.response.DebateListResponse
import com.debate.pangyeori.debate.event.DebateCreatedEvent
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
        hostPosition: DebatePosition,
        turnTimeSeconds: Int,
        freeDebateTimeSeconds: Int,
    ): DebateCreateResponse {
        val host = userRepository.findByEmail(
            email = hostEmail,
        ) ?: throw UserNotFoundException()
        val inviteToken = UUID.randomUUID().toString()
        val debate = debateRepository.save(
            Debate.create(
                host = host,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                hostPosition = hostPosition,
                turnTimeSeconds = turnTimeSeconds,
                freeDebateTimeSeconds = freeDebateTimeSeconds,
                inviteToken = inviteToken,
            ),
        )
        debateUserRepository.save(
            DebateUser.create(
                debate = debate,
                user = host,
                role = DebateUserRole.HOST,
                position = hostPosition,
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
        )
    }

    @Transactional(readOnly = true)
    fun getMyDebates(
        userEmail: String,
        status: List<DebateStatus>?,
        role: DebateUserRole?,
        keyword: String?,
        cursor: String?,
        pageSize: Int?,
    ): CursorPage<DebateListResponse> {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val limit = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val members = debateUserRepository.findAllParticipating(
            userId = user.id!!,
            status = status,
            role = role,
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
            cursor = cursor,
            limit = limit + 1,
        )
        val hasNext = members.size > limit
        val items = members.take(limit).map {
            DebateListResponse.from(
                member = it,
            )
        }

        return CursorPage(
            items = items,
            nextCursor = if (hasNext) items.last().debateId else null,
            hasNext = hasNext,
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 50
    }
}
