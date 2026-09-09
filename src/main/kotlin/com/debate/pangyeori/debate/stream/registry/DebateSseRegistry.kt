package com.debate.pangyeori.debate.stream.registry

import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.stream.message.DebateStreamEvents
import com.debate.pangyeori.debate.stream.message.DebateStreamMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.hypersistence.tsid.TSID
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class DebateSseRegistry {

    private val logger = KotlinLogging.logger {}

    private val subscriptionsByDebateId = ConcurrentHashMap<String, ConcurrentHashMap<String, Subscription>>()

    fun register(
        debateId: String,
        userId: String,
        role: DebateUserRole,
        emitter: SseEmitter,
    ): String {
        val subscriptionId = TSID.fast().toString()
        subscriptionsByDebateId
            .computeIfAbsent(debateId) { ConcurrentHashMap() }[subscriptionId] = Subscription(
            userId = userId,
            role = role,
            emitter = emitter,
        )

        return subscriptionId
    }

    fun remove(
        debateId: String,
        subscriptionId: String,
    ) {
        subscriptionsByDebateId[debateId]?.remove(subscriptionId)
    }

    fun sendSnapshot(
        debateId: String,
        subscriptionId: String,
        payload: Any,
    ): Boolean {
        val subscription = subscriptionsByDebateId[debateId]?.get(subscriptionId) ?: return false

        return send(
            debateId = debateId,
            subscriptionId = subscriptionId,
            subscription = subscription,
            eventName = DebateStreamEvents.SNAPSHOT,
            payload = payload,
        )
    }

    fun dispatch(
        message: DebateStreamMessage,
    ) {
        val subscriptions = subscriptionsByDebateId[message.debateId] ?: return
        subscriptions.forEach { (subscriptionId, subscription) ->
            if (!message.target.matches(
                    subscriberUserId = subscription.userId,
                    subscriberRole = subscription.role,
                )
            ) {
                return@forEach
            }
            if (message.eventName != null && message.data != null) {
                send(
                    debateId = message.debateId,
                    subscriptionId = subscriptionId,
                    subscription = subscription,
                    eventName = message.eventName,
                    payload = message.data,
                )
            }
            if (message.close) {
                runCatching { subscription.emitter.complete() }
                subscriptions.remove(subscriptionId)
            }
        }
    }

    fun sendHeartbeats() {
        subscriptionsByDebateId.forEach { (debateId, subscriptions) ->
            subscriptions.forEach { (subscriptionId, subscription) ->
                runCatching {
                    synchronized(subscription.lock) {
                        subscription.emitter.send(
                            SseEmitter.event().comment(HEARTBEAT_COMMENT),
                        )
                    }
                }.onFailure {
                    discard(
                        debateId = debateId,
                        subscriptionId = subscriptionId,
                        subscription = subscription,
                        cause = it,
                    )
                }
            }
        }
    }

    private fun send(
        debateId: String,
        subscriptionId: String,
        subscription: Subscription,
        eventName: String,
        payload: Any,
    ): Boolean = runCatching {
        synchronized(subscription.lock) {
            subscription.emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON),
            )
        }
    }.onFailure {
        discard(
            debateId = debateId,
            subscriptionId = subscriptionId,
            subscription = subscription,
            cause = it,
        )
    }.isSuccess

    private fun discard(
        debateId: String,
        subscriptionId: String,
        subscription: Subscription,
        cause: Throwable,
    ) {
        remove(
            debateId = debateId,
            subscriptionId = subscriptionId,
        )
        runCatching { subscription.emitter.completeWithError(cause) }
        logger.debug(cause) { "끊긴 SSE 연결을 정리했습니다. debateId=$debateId" }
    }

    private class Subscription(
        val userId: String,
        val role: DebateUserRole,
        val emitter: SseEmitter,
    ) {
        val lock = Any()
    }

    companion object {
        private const val HEARTBEAT_COMMENT = "keepalive"
    }
}
