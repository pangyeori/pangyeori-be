package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.domain.enums.DebateUserStatus
import com.debate.pangyeori.debate.event.DebateStartedEvent
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.exception.DebateNotFoundException
import com.debate.pangyeori.debate.exception.DebateNotReadyException
import com.debate.pangyeori.debate.exception.DebateAlreadyConnectedException
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository
import com.debate.pangyeori.debate.repository.DebatePresenceRedisRepository.Member
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.websocket.DebateInstance
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.config.SchedulingConfig
import com.debate.pangyeori.debate.websocket.publisher.RedisDebateConnectionEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

@Service
class DebateConnectionService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val debatePresenceRedisRepository: DebatePresenceRedisRepository,
    private val debateConnectionEventPublisher: RedisDebateConnectionEventPublisher,
    private val eventPublisher: ApplicationEventPublisher,
    @param:Qualifier(SchedulingConfig.DEBATE_CONNECTION_SCHEDULER) private val taskScheduler: TaskScheduler,
    private val transactionTemplate: TransactionTemplate,
    private val debateInstance: DebateInstance,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun validateEntry(
        debateId: String,
        userId: String,
        sessionId: String,
    ) {
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        )
        if (member == null || member.status != DebateUserStatus.ACCEPTED) {
            throw DebateAccessDeniedException()
        }
        val debate = debateRepository.findById(debateId).orElseThrow { DebateNotFoundException() }
        if (debate.status != DebateStatus.READY && debate.status != DebateStatus.IN_PROGRESS) {
            throw DebateNotReadyException()
        }
        val connectedDebateId = debatePresenceRedisRepository.findDebateIdBySession(sessionId)
        if (connectedDebateId != null && connectedDebateId != debateId) {
            throw DebateAlreadyConnectedException()
        }
    }

    fun enter(
        debateId: String,
        userId: String,
        sessionId: String,
    ) {
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = userId,
        ) ?: return
        val userAlreadyPresent = debatePresenceRedisRepository.findActiveMembers(debateId).any { it.userId == userId }
        debatePresenceRedisRepository.refreshInstance(debateInstance.id)
        debatePresenceRedisRepository.save(
            debateId = debateId,
            sessionId = sessionId,
            member = Member(
                userId = userId,
                role = member.role,
                instanceId = debateInstance.id,
            ),
        )
        if (!userAlreadyPresent) {
            debateConnectionEventPublisher.publish(
                DebateConnectionEvent(
                    debateId = debateId,
                    type = DebateConnectionEvent.Type.ENTERED,
                    role = member.role,
                ),
            )
            logger.info { "토론방에 입장했습니다. debateId=$debateId, userId=$userId" }
        }

        if (hasBothSides(debateId)) {
            transactionTemplate.executeWithoutResult {
                startIfReady(debateId)
            }
        }
    }

    fun leave(
        sessionId: String,
    ) {
        val debateId = debatePresenceRedisRepository.findDebateIdBySession(sessionId) ?: return
        val member = debatePresenceRedisRepository.find(
            debateId = debateId,
            sessionId = sessionId,
        ) ?: return
        debatePresenceRedisRepository.remove(
            debateId = debateId,
            sessionId = sessionId,
        )
        if (debatePresenceRedisRepository.findActiveMembers(debateId).any { it.userId == member.userId }) {
            return
        }
        debateConnectionEventPublisher.publish(
            DebateConnectionEvent(
                debateId = debateId,
                type = DebateConnectionEvent.Type.LEFT,
                role = member.role,
            ),
        )
        logger.info { "토론방에서 이탈했습니다. debateId=$debateId, userId=${member.userId}" }

        if (isReady(debateId)) {
            scheduleDisconnectCheck(
                debateId = debateId,
                userId = member.userId,
            )
        }
    }

    private fun startIfReady(
        debateId: String,
    ) {
        val debate = debateRepository.findWithLockById(debateId) ?: return
        if (debate.status != DebateStatus.READY) return

        debate.start()
        eventPublisher.publishEvent(
            DebateStatusChangedEvent(
                debateId = debateId,
                status = debate.status,
            ),
        )
        eventPublisher.publishEvent(
            DebateStartedEvent(
                debateId = debateId,
            ),
        )
        logger.info { "토론을 시작했습니다. debateId=$debateId" }
    }

    private fun scheduleDisconnectCheck(
        debateId: String,
        userId: String,
    ) {
        taskScheduler.schedule(
            { confirmDisconnect(debateId = debateId, userId = userId) },
            Instant.now().plus(RECONNECT_GRACE_PERIOD),
        )
    }

    private fun confirmDisconnect(
        debateId: String,
        userId: String,
    ) {
        if (debatePresenceRedisRepository.findActiveMembers(debateId).any { it.userId == userId }) return

        transactionTemplate.executeWithoutResult {
            val debate = debateRepository.findById(debateId).orElse(null) ?: return@executeWithoutResult
            if (debate.status != DebateStatus.READY) return@executeWithoutResult
            val member = debateUserRepository.findByDebateIdAndUserId(
                debateId = debateId,
                userId = userId,
            ) ?: return@executeWithoutResult
            member.increaseDisconnectCount()
        }
        logger.info { "재접속 없이 이탈이 확정되어 끊김 횟수를 증가시켰습니다. debateId=$debateId, userId=$userId" }
    }

    private fun hasBothSides(
        debateId: String,
    ): Boolean {
        val roles = debatePresenceRedisRepository.findActiveMembers(debateId).map { it.role }.toSet()
        return roles.containsAll(BOTH_SIDES)
    }

    private fun isReady(
        debateId: String,
    ) = debateRepository.findById(debateId).map { it.status == DebateStatus.READY }.orElse(false)

    companion object {
        private val RECONNECT_GRACE_PERIOD = Duration.ofSeconds(10)
        private val BOTH_SIDES = setOf(DebateUserRole.HOST, DebateUserRole.GUEST)
    }
}
