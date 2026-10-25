package com.debate.pangyeori.debate.service

import com.debate.pangyeori.config.SchedulingConfig
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.dto.response.DebateEarlyEndResponse
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndAlreadyProposedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndNotProposedException
import com.debate.pangyeori.debate.exception.DebateEarlyEndSelfResponseNotAllowedException
import com.debate.pangyeori.debate.exception.DebateNotFoundException
import com.debate.pangyeori.debate.exception.DebateNotFreeStageException
import com.debate.pangyeori.debate.repository.DebateEarlyEndRedisRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Service
class DebateEarlyEndService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val userRepository: UserRepository,
    private val debateEarlyEndRedisRepository: DebateEarlyEndRedisRepository,
    private val debateFreeMessageService: DebateFreeMessageService,
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
    @param:Qualifier(SchedulingConfig.DEBATE_CONNECTION_SCHEDULER) private val taskScheduler: TaskScheduler,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getStatus(
        debateId: String,
        userEmail: String,
    ): DebateEarlyEndResponse {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw DebateAccessDeniedException()

        val proposal = debateEarlyEndRedisRepository.find(debateId = debateId)
        return DebateEarlyEndResponse(
            pending = proposal != null,
            proposerRole = proposal?.proposerRole,
            expiresAt = proposal?.expiresAt,
        )
    }

    fun propose(
        debateId: String,
        userId: String,
    ) {
        val debate = debateRepository.findById(debateId).orElseThrow { DebateNotFoundException() }
        if (debate.status != DebateStatus.IN_PROGRESS || debate.currentStage != DebateStage.FREE) {
            throw DebateNotFreeStageException()
        }
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: throw DebateAccessDeniedException()

        val expiresAt = debateEarlyEndRedisRepository.propose(
            debateId = debateId,
            proposerId = userId,
            proposerRole = member.role,
        ) ?: throw DebateEarlyEndAlreadyProposedException()

        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.EARLY_END_PROPOSED,
                role = member.role,
                expiresAt = expiresAt,
            ),
        )

        taskScheduler.schedule(
            { expireIfStillPending(debateId = debateId, proposerId = userId) },
            expiresAt.toInstant(ZoneOffset.UTC),
        )
        logger.info { "조기 종료를 제안했습니다. debateId=$debateId" }
    }

    fun accept(
        debateId: String,
        userId: String,
    ) {
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: throw DebateAccessDeniedException()
        val proposal = debateEarlyEndRedisRepository.find(debateId = debateId)
            ?: throw DebateEarlyEndNotProposedException()
        if (proposal.proposerId == userId) throw DebateEarlyEndSelfResponseNotAllowedException()

        debateEarlyEndRedisRepository.clear(debateId = debateId)
        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.EARLY_END_ACCEPTED,
                role = member.role,
            ),
        )
        debateFreeMessageService.endByAgreement(debateId = debateId)
        logger.info { "조기 종료 제안을 수락했습니다. debateId=$debateId" }
    }

    fun decline(
        debateId: String,
        userId: String,
    ) {
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: throw DebateAccessDeniedException()
        val proposal = debateEarlyEndRedisRepository.find(debateId = debateId)
            ?: throw DebateEarlyEndNotProposedException()
        if (proposal.proposerId == userId) throw DebateEarlyEndSelfResponseNotAllowedException()

        debateEarlyEndRedisRepository.clear(debateId = debateId)
        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.EARLY_END_DECLINED,
                role = member.role,
            ),
        )
        logger.info { "조기 종료 제안을 거절했습니다. debateId=$debateId" }
    }

    fun cancel(
        debateId: String,
        userId: String,
    ) {
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: throw DebateAccessDeniedException()
        val proposal = debateEarlyEndRedisRepository.find(debateId = debateId)
            ?: throw DebateEarlyEndNotProposedException()
        if (proposal.proposerId != userId) throw DebateAccessDeniedException()

        debateEarlyEndRedisRepository.clear(debateId = debateId)
        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.EARLY_END_CANCELLED,
                role = member.role,
            ),
        )
        logger.info { "조기 종료 제안을 취소했습니다. debateId=$debateId" }
    }

    private fun expireIfStillPending(
        debateId: String,
        proposerId: String,
    ) {
        val proposal = debateEarlyEndRedisRepository.find(debateId = debateId) ?: return
        if (proposal.proposerId != proposerId) return

        debateEarlyEndRedisRepository.clear(debateId = debateId)
        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.EARLY_END_EXPIRED,
                role = proposal.proposerRole,
            ),
        )
        logger.info { "응답이 없어 조기 종료 제안이 만료되었습니다. debateId=$debateId" }
    }
}
