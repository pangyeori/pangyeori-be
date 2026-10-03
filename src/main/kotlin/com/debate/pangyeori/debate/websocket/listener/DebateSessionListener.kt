package com.debate.pangyeori.debate.websocket.listener

import com.debate.pangyeori.debate.service.DebateConnectionService
import com.debate.pangyeori.debate.websocket.DebateDestination
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent

@Component
class DebateSessionListener(
    private val debateConnectionService: DebateConnectionService,
) {
    @EventListener
    fun handleSubscribe(
        event: SessionSubscribeEvent,
    ) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val debateId = DebateDestination.debateIdOf(accessor.destination) ?: return
        val userId = accessor.user?.name ?: return
        val sessionId = accessor.sessionId ?: return

        debateConnectionService.enter(
            debateId = debateId,
            userId = userId,
            sessionId = sessionId,
        )
    }

    @EventListener
    fun handleUnsubscribe(
        event: SessionUnsubscribeEvent,
    ) {
        val sessionId = StompHeaderAccessor.wrap(event.message).sessionId ?: return
        debateConnectionService.leave(sessionId)
    }

    @EventListener
    fun handleDisconnect(
        event: SessionDisconnectEvent,
    ) {
        debateConnectionService.leave(event.sessionId)
    }
}
