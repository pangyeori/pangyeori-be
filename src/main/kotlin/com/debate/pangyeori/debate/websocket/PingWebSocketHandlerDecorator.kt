package com.debate.pangyeori.debate.websocket

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.Session
import org.springframework.scheduling.TaskScheduler
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

class PingWebSocketHandlerDecorator(
    delegate: WebSocketHandler,
    private val taskScheduler: TaskScheduler,
) : WebSocketHandlerDecorator(delegate) {

    private val logger = KotlinLogging.logger {}
    private val pingTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val monitors = ConcurrentHashMap<String, PingMonitor>()

    override fun afterConnectionEstablished(
        session: WebSocketSession,
    ) {
        super.afterConnectionEstablished(session)
        val nativeSession = (session as? StandardWebSocketSession)
            ?.getNativeSession(Session::class.java)
            ?: return

        val monitor = PingMonitor(PONG_TIMEOUT, Clock.systemUTC())
        monitors[session.id] = monitor
        pingTasks[session.id] = taskScheduler.scheduleAtFixedRate(
            { sendPingOrClose(session, nativeSession, monitor) },
            PING_INTERVAL,
        )
    }

    override fun handleMessage(
        session: WebSocketSession,
        message: WebSocketMessage<*>,
    ) {
        if (message is PongMessage) {
            monitors[session.id]?.recordPong()
            return
        }
        super.handleMessage(session, message)
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        closeStatus: CloseStatus,
    ) {
        pingTasks.remove(session.id)?.cancel(false)
        monitors.remove(session.id)
        super.afterConnectionClosed(session, closeStatus)
    }

    private fun sendPingOrClose(
        session: WebSocketSession,
        nativeSession: Session,
        monitor: PingMonitor,
    ) {
        if (monitor.isExpired()) {
            logger.warn { "pong 응답이 없어 연결을 닫습니다. sessionId=${session.id}" }
            runCatching { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
            return
        }
        runCatching {
            nativeSession.basicRemote.sendPing(ByteBuffer.allocate(0))
        }.onFailure {
            logger.debug(it) { "ping 전송에 실패하여 연결을 닫습니다. sessionId=${session.id}" }
            runCatching { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
        }
    }

    companion object {
        private val PING_INTERVAL: Duration = Duration.ofSeconds(10)
        private val PONG_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
