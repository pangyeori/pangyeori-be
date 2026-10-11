package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateHistory
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.dto.response.DebateHistoryResponse
import com.debate.pangyeori.debate.event.DebateHistoryRecordedEvent
import com.debate.pangyeori.debate.event.DebateStageChangedEvent
import com.debate.pangyeori.debate.exception.*
import com.debate.pangyeori.debate.repository.DebateHistoryRepository
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
class DebateHistoryService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val debateHistoryRepository: DebateHistoryRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun submit(
        debateId: String,
        userEmail: String,
        content: String,
    ): DebateHistoryResponse {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val debate = debateRepository.findWithLockById(
            id = debateId,
        ) ?: throw DebateNotFoundException()
        if (debate.status != DebateStatus.IN_PROGRESS) {
            throw DebateNotReadyException()
        }

        val stage = debate.currentStage
        val speakingPosition = positionForStage(stage) ?: throw DebateNotYourTurnException()
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw DebateAccessDeniedException()
        if (member.position != speakingPosition) {
            throw DebateNotYourTurnException()
        }
        if (debateHistoryRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) {
            throw DebateHistoryAlreadySubmittedException()
        }

        val history = debateHistoryRepository.save(
            DebateHistory.create(
                debate = debate,
                user = user,
                stage = stage,
                content = content,
            ),
        )
        advance(
            debate = debate,
            finishedStage = stage,
            role = member.role,
            content = content,
        )
        logger.info { "토론 발언을 제출했습니다. debateId=$debateId, stage=$stage" }

        return DebateHistoryResponse.from(
            history = history,
        )
    }

    @Transactional
    fun getHistories(
        debateId: String,
        userEmail: String,
    ): List<DebateHistoryResponse> {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw DebateAccessDeniedException()

        forceAdvanceIfOverdue(
            debateId = debateId,
        )

        return debateHistoryRepository.findAllByDebateIdOrderByCreatedAtAsc(
            debateId = debateId,
        ).map { DebateHistoryResponse.from(history = it) }
    }

    @Transactional
    fun forceAdvanceIfOverdue(
        debateId: String,
    ) {
        val debate = debateRepository.findWithLockById(id = debateId) ?: return
        val stage = debate.currentStage
        if (!isTimedStage(stage)) return
        val deadline = debate.currentTurnEndsAt() ?: return
        if (LocalDateTime.now(ZoneOffset.UTC).isBefore(deadline)) return
        if (debateHistoryRepository.findByDebateIdAndStage(debateId = debateId, stage = stage) != null) {
            return
        }

        val position = positionForStage(stage) ?: return
        val member = debateUserRepository.findByDebateIdAndPosition(
            debateId = debateId,
            position = position,
        ) ?: return

        debateHistoryRepository.save(
            DebateHistory.create(
                debate = debate,
                user = member.user,
                stage = stage,
                content = null,
            ),
        )
        advance(
            debate = debate,
            finishedStage = stage,
            role = member.role,
            content = null,
        )
        logger.info { "턴 시간이 초과되어 무응답으로 다음 단계로 넘어갔습니다. debateId=$debateId, stage=$stage" }
    }

    private fun advance(
        debate: Debate,
        finishedStage: DebateStage,
        role: DebateUserRole,
        content: String?,
    ) {
        eventPublisher.publishEvent(
            DebateHistoryRecordedEvent(
                debateId = debate.id!!,
                stage = finishedStage,
                role = role,
                content = content,
            ),
        )

        val nextStage = nextStage(finishedStage)
        debate.advanceStage(next = nextStage)

        eventPublisher.publishEvent(
            DebateStageChangedEvent(
                debateId = debate.id!!,
                stage = nextStage,
                turnEndsAt = debate.currentTurnEndsAt(),
            ),
        )
    }

    private fun positionForStage(
        stage: DebateStage,
    ): DebatePosition? = when (stage) {
        DebateStage.OPENING_PROS -> DebatePosition.PROS
        DebateStage.OPENING_CONS -> DebatePosition.CONS
        else -> null
    }

    private fun isTimedStage(
        stage: DebateStage,
    ) = stage == DebateStage.OPENING_PROS || stage == DebateStage.OPENING_CONS

    private fun nextStage(
        current: DebateStage,
    ) = when (current) {
        DebateStage.OPENING_PROS -> DebateStage.OPENING_CONS
        DebateStage.OPENING_CONS -> DebateStage.VERDICT_1
        else -> throw IllegalStateException("이 단계에서는 다음 단계로 넘어갈 수 없습니다: $current")
    }
}
