package com.debate.pangyeori.support.asyncapi.generator

import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 문서화 테스트가 쓰는 STOMP 클라이언트. 목적지 하나를 구독하고 도착한 프레임을 순서대로 큐에 쌓는다.
 *
 * 구독이 서버에 등록되기 전에 트리거가 발행되면 첫 프레임을 놓치므로, [connect]는 구독 후 짧게 기다린다.
 */
internal class DocumentingStompClient(
    private val url: String,
    private val connectHeaders: Map<String, String>,
    private val destination: String,
) : AutoCloseable {

    private val frames = LinkedBlockingQueue<String>()
    private val stompClient = WebSocketStompClient(StandardWebSocketClient())
    private var session: StompSession? = null

    fun connect() {
        val headers = StompHeaders().apply {
            connectHeaders.forEach { (name, value) -> add(name, value) }
        }
        val connected = stompClient.connectAsync(
            url,
            WebSocketHttpHeaders(),
            headers,
            object : StompSessionHandlerAdapter() {},
        ).get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        session = connected

        connected.subscribe(
            destination,
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    (payload as? ByteArray)?.let { frames.add(String(it, StandardCharsets.UTF_8)) }
                }
            },
        )
        Thread.sleep(SUBSCRIBE_SETTLE.toMillis())
    }

    fun nextFrame(
        timeout: Duration,
    ): String = frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
        ?: throw IllegalStateException("STOMP 메시지를 ${timeout.seconds}초 안에 받지 못했습니다: $destination")

    override fun close() {
        session?.disconnect()
        session = null
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val SUBSCRIBE_SETTLE: Duration = Duration.ofMillis(300)
    }
}
