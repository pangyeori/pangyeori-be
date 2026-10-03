package com.debate.pangyeori.debate.websocket.interceptor

import com.debate.pangyeori.auth.exception.InvalidTokenException
import com.debate.pangyeori.auth.token.TokenProvider
import com.debate.pangyeori.debate.exception.DebateAccessDeniedException
import com.debate.pangyeori.debate.service.DebateConnectionService
import com.debate.pangyeori.debate.websocket.DebateDestination
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

@Component
class StompChannelInterceptor(
    private val tokenProvider: TokenProvider,
    private val userRepository: UserRepository,
    private val debateConnectionService: DebateConnectionService,
) : ChannelInterceptor {

    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeSubscribe(accessor)
            else -> Unit
        }
        return message
    }

    private fun authenticate(
        accessor: StompHeaderAccessor,
    ) {
        val token = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?: throw InvalidTokenException()
        val claims = tokenProvider.parseAccessToken(token)
        val user = userRepository.findByEmail(
            email = claims.subject,
        )?.takeIf { it.status == UserStatus.ACTIVE }
            ?: throw InvalidTokenException()

        accessor.user = UsernamePasswordAuthenticationToken(user.id!!, null, emptyList())
    }

    private fun authorizeSubscribe(
        accessor: StompHeaderAccessor,
    ) {
        val debateId = DebateDestination.debateIdOf(accessor.destination)
            ?: throw DebateAccessDeniedException()
        val userId = accessor.user?.name ?: throw InvalidTokenException()
        val sessionId = accessor.sessionId ?: throw InvalidTokenException()

        debateConnectionService.validateEntry(
            debateId = debateId,
            userId = userId,
            sessionId = sessionId,
        )
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
