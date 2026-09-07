package com.debate.pangyeori.debate.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.*
import com.debate.pangyeori.debate.dto.response.*
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent
import com.debate.pangyeori.debate.event.DebateQueueChangedEvent.DebateQueueOperation
import com.debate.pangyeori.debate.event.DebateStatusChangedEvent
import com.debate.pangyeori.debate.exception.*
import com.debate.pangyeori.debate.repository.DebateInviteRedisRepository
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateQueueRedisRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.repository.DebateStatusRedisRepository
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class DebateParticipationService(
    private val debateRepository: DebateRepository,
    private val debateUserRepository: DebateUserRepository,
    private val debateInviteRedisRepository: DebateInviteRedisRepository,
    private val debateQueueRedisRepository: DebateQueueRedisRepository,
    private val debateStatusRedisRepository: DebateStatusRedisRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getInvitation(
        token: String,
        userEmail: String,
    ): DebateInvitationResponse {
        val debate = findByToken(
            token = token,
        )
        validateInviteExpiration(
            debate = debate,
        )
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val debateId = debate.id!!
        val guestStatus = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        )?.status

        validateInvitationState(
            debate = debate,
            guestStatus = guestStatus,
        )

        return DebateInvitationResponse(
            debateId = debateId,
            title = debate.title,
            guestPosition = debate.hostPosition.opposite(),
            debateStatus = debate.status,
            guestStatus = guestStatus,
        )
    }

    @Transactional
    fun requestParticipation(
        debateId: String,
        userEmail: String,
    ): DebateParticipationResponse {
        val debate = debateRepository.findWithLockById(
            id = debateId,
        ) ?: throw DebateNotFoundException()
        validateInviteExpiration(
            debate = debate,
        )
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        if (debate.host.id == user.id) {
            throw SelfJoinNotAllowedException()
        }

        val existingMember = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        )
        when (existingMember?.status) {
            DebateUserStatus.PENDING -> throw AlreadyInQueueException()
            DebateUserStatus.CANCELLED -> throw AlreadyCancelledException()
            DebateUserStatus.REJECTED -> throw AlreadyRejectedException()
            DebateUserStatus.ACCEPTED -> throw DebateAlreadyMatchedException()
            null -> Unit
        }
        validateWaitingState(
            debate = debate,
        )

        debateUserRepository.save(
            DebateUser.create(
                debate = debate,
                user = user,
                role = DebateUserRole.GUEST,
                position = debate.hostPosition.opposite(),
            ),
        )
        publishQueueChange(
            debateId = debateId,
            userId = user.id,
            operation = DebateQueueOperation.ADD,
        )

        return DebateParticipationResponse(
            debateId = debateId,
            position = debate.hostPosition.opposite(),
        )
    }

    @Transactional
    fun cancelParticipation(
        debateId: String,
        userEmail: String,
    ) {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw NotInPendingStatusException()
        if (member.status != DebateUserStatus.PENDING) {
            throw NotInPendingStatusException()
        }

        member.cancel()
        publishQueueChange(
            debateId = debateId,
            userId = user.id,
            operation = DebateQueueOperation.REMOVE,
        )
    }

    @Transactional
    fun acceptGuest(
        debateId: String,
        hostEmail: String,
        guestUserId: String,
    ): DebateGuestAcceptResponse {
        val debate = debateRepository.findWithLockById(
            id = debateId,
        ) ?: throw DebateNotFoundException()
        if (debate.status != DebateStatus.WAITING) {
            throw DebateNotWaitingException()
        }
        if (debate.host.email != hostEmail) {
            throw DebateAccessDeniedException()
        }
        val selectedMember = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = guestUserId,
        )
        if (selectedMember == null || selectedMember.status != DebateUserStatus.PENDING) {
            throw UserNotFoundInQueueException()
        }

        selectedMember.accept()
        debateUserRepository.findAllByDebateIdAndStatus(
            debateId = debateId,
            status = DebateUserStatus.PENDING,
        ).filter { it.id != selectedMember.id }.forEach { it.reject() }
        debate.acceptGuest(
            selectedGuest = selectedMember.user,
        )
        publishQueueChange(
            debateId = debateId,
            userId = null,
            operation = DebateQueueOperation.CLEAR,
        )
        eventPublisher.publishEvent(
            DebateStatusChangedEvent(
                debateId = debateId,
                status = debate.status,
            ),
        )

        return DebateGuestAcceptResponse(
            debateId = debateId,
            status = debate.status,
        )
    }

    @Transactional(readOnly = true)
    fun getStatus(
        debateId: String,
        userEmail: String,
    ): DebateStatusResponse {
        val user = userRepository.findByEmail(
            email = userEmail,
        ) ?: throw UserNotFoundException()
        val member = debateUserRepository.findByDebateIdAndUserId(
            debateId = debateId,
            userId = user.id!!,
        ) ?: throw DebateAccessDeniedException()
        val isHost = member.role == DebateUserRole.HOST
        val debateStatus = findDebateStatus(
            debateId = debateId,
        )
        val requestList = if (isHost) {
            val pendingMembers = debateUserRepository.findAllByDebateIdAndStatusOrderByCreatedAtAsc(
                debateId = debateId,
                status = DebateUserStatus.PENDING,
            )
            val cachedOrder = runCatching {
                debateQueueRedisRepository.findAll(
                    debateId = debateId,
                )
            }.getOrNull()
            val orderedMembers = if (cachedOrder == null) {
                restoreQueueCache(
                    debateId = debateId,
                    pendingMembers = pendingMembers,
                )
                pendingMembers
            } else {
                val orderByUserId = cachedOrder.withIndex().associate { it.value to it.index }
                pendingMembers.sortedBy { orderByUserId[it.user.id] ?: Int.MAX_VALUE }
            }
            orderedMembers.map {
                DebateStatusResponse.ParticipationRequest(
                    userId = it.user.id!!,
                    nickname = it.user.nickname,
                    status = it.status,
                    requestedAt = it.createdAt!!,
                )
            }
        } else {
            null
        }

        return DebateStatusResponse(
            debateStatus = debateStatus,
            guestStatus = if (isHost) null else member.status,
            requestList = requestList,
        )
    }

    private fun findByToken(
        token: String,
    ): Debate {
        val cachedDebateId = runCatching {
            debateInviteRedisRepository.findDebateId(
                token = token,
            )
        }.onFailure {
            logger.warn(it) { "초대 토큰 캐시 조회에 실패했습니다." }
        }.getOrNull()
        if (cachedDebateId != null) {
            return debateRepository.findById(cachedDebateId).orElseThrow { InviteTokenExpiredException() }
        }

        val debate = debateRepository.findByInviteToken(
            inviteToken = token,
        ) ?: throw InviteTokenExpiredException()
        runCatching {
            debateInviteRedisRepository.save(
                token = token,
                debateId = debate.id!!,
            )
        }.onFailure {
            logger.warn(it) { "초대 토큰 캐시 복구에 실패했습니다. debateId=${debate.id}" }
        }

        return debate
    }

    private fun validateInviteExpiration(
        debate: Debate,
    ) {
        if (
            debate.createdAt?.plusHours(INVITE_TOKEN_VALID_HOURS)
                ?.isBefore(LocalDateTime.now(ZoneOffset.UTC)) == true
        ) {
            throw InviteTokenExpiredException()
        }
    }

    private fun validateInvitationState(
        debate: Debate,
        guestStatus: DebateUserStatus?,
    ) {
        if (guestStatus == DebateUserStatus.ACCEPTED) return
        validateWaitingState(
            debate = debate,
        )
    }

    private fun validateWaitingState(
        debate: Debate,
    ) {
        when (debate.status) {
            DebateStatus.WAITING -> Unit
            DebateStatus.READY -> throw DebateAlreadyMatchedException()
            DebateStatus.IN_PROGRESS, DebateStatus.PAUSED -> throw DebateInProgressException()
            DebateStatus.FINISHED -> throw DebateFinishedException()
            DebateStatus.CANCELLED -> throw DebateCancelledException()
        }
    }

    private fun publishQueueChange(
        debateId: String,
        userId: String?,
        operation: DebateQueueOperation,
    ) {
        eventPublisher.publishEvent(
            DebateQueueChangedEvent(
                debateId = debateId,
                userId = userId,
                operation = operation,
            ),
        )
    }

    private fun findDebateStatus(
        debateId: String,
    ): DebateStatus {
        val cachedStatus = runCatching {
            debateStatusRedisRepository.find(
                debateId = debateId,
            )
        }.onFailure {
            logger.warn(it) { "토론방 상태 캐시 조회에 실패했습니다. debateId=$debateId" }
        }.getOrNull()
        if (cachedStatus != null) return cachedStatus

        val debate = debateRepository.findById(debateId).orElseThrow { DebateNotFoundException() }
        runCatching {
            debateStatusRedisRepository.save(
                debateId = debateId,
                status = debate.status,
            )
        }.onFailure {
            logger.warn(it) { "토론방 상태 캐시 복구에 실패했습니다. debateId=$debateId" }
        }

        return debate.status
    }

    private fun restoreQueueCache(
        debateId: String,
        pendingMembers: List<DebateUser>,
    ) {
        runCatching {
            debateQueueRedisRepository.replace(
                debateId = debateId,
                userIds = pendingMembers.map { it.user.id!! },
            )
        }.onFailure {
            logger.warn(it) { "토론방 참여 대기열 캐시 복구에 실패했습니다. debateId=$debateId" }
        }
    }

    companion object {
        private const val INVITE_TOKEN_VALID_HOURS = 24L
    }
}
