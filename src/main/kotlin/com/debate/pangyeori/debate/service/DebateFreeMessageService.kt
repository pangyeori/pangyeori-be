package com.debate.pangyeori.debate.service

import com.debate.pangyeori.common.dto.CursorPage
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateFreeMessage
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.dto.response.DebateFreeMessageResponse
import com.debate.pangyeori.debate.event.DebateFreeMessageSentEvent
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateNotFoundException
import com.debate.pangyeori.debate.exception.DebateNotFreeStageException
import com.debate.pangyeori.debate.repository.DebateFreeMessageRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class DebateFreeMessageService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val debateFreeMessageRepository: DebateFreeMessageRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun submit(
        debateId: String,
        userId: String,
        content: String,
    ): DebateFreeMessageResponse {
        val debate = debateRepository.findWithLockById(
            id = debateId,
        ) ?: throw DebateNotFoundException()
        if (debate.status != DebateStatus.IN_PROGRESS || debate.currentStage != DebateStage.FREE) {
            throw DebateNotFreeStageException()
        }
        val deadline = debate.currentStageEndsAt()
        if (deadline != null && !LocalDateTime.now(ZoneOffset.UTC).isBefore(deadline)) {
            throw DebateNotFreeStageException()
        }
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: throw DebateAccessDeniedException()

        val message = debateFreeMessageRepository.save(
            DebateFreeMessage.create(
                debate = debate,
                user = member.user,
                role = member.role,
                content = content,
            ),
        )

        eventPublisher.publishEvent(
            DebateFreeMessageSentEvent(
                debateId = debate.id!!,
                messageId = message.id!!,
                role = message.role,
                content = message.content,
                createdAt = message.createdAt!!,
            ),
        )
        logger.info { "자유 토론 메시지를 제출했습니다. debateId=$debateId" }

        return DebateFreeMessageResponse.from(
            message = message,
        )
    }

    @Transactional
    fun getMessages(
        debateId: String,
        userEmail: String,
        cursor: String?,
        pageSize: Int?,
    ): CursorPage<DebateFreeMessageResponse> {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw DebateAccessDeniedException()

        forceEndIfOverdue(
            debateId = debateId,
        )

        val limit = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val messages = debateFreeMessageRepository.findAllByDebateId(
            debateId = debateId,
            cursor = cursor,
            limit = limit + 1,
        )
        val hasNext = messages.size > limit
        val items = messages.take(limit).map { DebateFreeMessageResponse.from(message = it) }

        return CursorPage(
            items = items,
            nextCursor = if (hasNext) items.last().id else null,
            hasNext = hasNext,
        )
    }

    @Transactional
    fun forceEndIfOverdue(
        debateId: String,
    ) {
        val debate = debateRepository.findWithLockById(id = debateId) ?: return
        if (debate.status != DebateStatus.IN_PROGRESS || debate.currentStage != DebateStage.FREE) return
        val deadline = debate.currentStageEndsAt() ?: return
        if (LocalDateTime.now(ZoneOffset.UTC).isBefore(deadline)) return

        advanceToFinalVerdict(debate = debate)
        logger.info { "자유 토론 시간이 초과되어 최종 판결 단계로 넘어갔습니다. debateId=$debateId" }
    }

    @Transactional
    fun endByAgreement(
        debateId: String,
    ) {
        val debate = debateRepository.findWithLockById(id = debateId) ?: return
        if (debate.currentStage != DebateStage.FREE) return

        advanceToFinalVerdict(debate = debate)
        logger.info { "양측 합의로 자유 토론을 조기 종료했습니다. debateId=$debateId" }
    }

    private fun advanceToFinalVerdict(
        debate: Debate,
    ) {
        debate.advanceStage(next = DebateStage.FINAL_VERDICT)
        eventPublisher.publishEvent(
            DebateStageChangedEvent(
                debateId = debate.id!!,
                stage = DebateStage.FINAL_VERDICT,
                turnEndsAt = debate.currentStageEndsAt(),
            ),
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 50
    }
}
