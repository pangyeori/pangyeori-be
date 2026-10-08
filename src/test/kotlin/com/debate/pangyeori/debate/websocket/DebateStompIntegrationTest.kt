package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.auth.token.TokenProvider
import com.debate.pangyeori.config.WebSocketConfig
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStatus
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
import com.debate.pangyeori.debate.websocket.message.DebateConnectionEvent
import com.debate.pangyeori.support.asyncapi.AsyncApiDocsTest
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DebateStompIntegrationTest : AsyncApiDocsTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var debateRepository: DebateRepository

    @Autowired
    private lateinit var debateUserRepository: DebateUserRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    private val sessions = mutableListOf<StompSession>()

    @Test
    fun `호스트와 게스트가 모두 입장하면 토론이 시작되고 시작 이벤트를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!
        val hostToken = tokenProvider.issue(host).accessToken

        documentStomp("debates/roomEvents", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                subscribeTo = DebateDestination.of("{debateId}"),
                description = "토론방 입장, 이탈, 시작 이벤트. 먼저 STOMP WebSocket으로 연결한 뒤 이 목적지를 구독한다. " +
                    "클라이언트는 이 채널에 메시지를 보내지 않고 구독만 한다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $hostToken")
                pathValue("debateId", debateId)
            }
            receive<DebateConnectionEvent>(
                summary = "개설자가 입장하면 자신의 입장 이벤트를 받는다",
                example = "개설자 입장",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (ENTERED, LEFT, STARTED)")
                field("role", "입장 또는 이탈한 참여자의 역할 (시작 이벤트에는 없음)").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.ENTERED
                    it.role shouldBe DebateUserRole.HOST
                }
            }
            receive<DebateConnectionEvent>(
                summary = "게스트가 입장하면 입장 이벤트를 받는다",
                example = "게스트 입장",
            ) {
                trigger { connectAsSubscriber(guest, debateId) }
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (ENTERED, LEFT, STARTED)")
                field("role", "입장 또는 이탈한 참여자의 역할 (시작 이벤트에는 없음)").optional()
                verify {
                    it.type shouldBe DebateConnectionEvent.Type.ENTERED
                    it.role shouldBe DebateUserRole.GUEST
                }
            }
            receive<DebateConnectionEvent>(
                summary = "두 참여자가 모두 입장하면 토론 시작 이벤트를 받는다",
                example = "토론 시작",
            ) {
                field("debateId", "토론방 ID")
                field("type", "이벤트 종류 (ENTERED, LEFT, STARTED)")
                field("role", "입장 또는 이탈한 참여자의 역할 (시작 이벤트에는 없음)").optional()
                verify { it.type shouldBe DebateConnectionEvent.Type.STARTED }
            }
        }

        debateRepository.findById(debateId).get().status shouldBe DebateStatus.IN_PROGRESS
    }

    @Test
    fun `유효하지 않은 토큰으로 연결하면 거절된다`() {
        val connected = runCatching {
            WebSocketStompClient(StandardWebSocketClient()).connectAsync(
                "ws://localhost:$port${WebSocketConfig.ENDPOINT}",
                WebSocketHttpHeaders(),
                StompHeaders().apply { add(AUTHORIZATION, "Bearer invalid-token") },
                object : StompSessionHandlerAdapter() {},
            ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrNull()

        (connected == null) shouldBe true
    }

    @Test
    fun `진행 중인 토론에서 끊긴 참여자는 다시 접속해 입장할 수 있다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!

        val hostFirst = connectAsSubscriber(host, debateId)
        val guestSubscriber = connectAsSubscriber(guest, debateId)
        awaitFrame(hostFirst.frames) { it.contains("\"type\":\"STARTED\"") }

        hostFirst.session.disconnect()

        val hostAgain = connectAsSubscriber(host, debateId)
        awaitFrame(hostAgain.frames) { it.contains("\"type\":\"ENTERED\"") && it.contains("\"role\":\"HOST\"") }
        guestSubscriber.session.disconnect()

        debateRepository.findById(debateId).get().status shouldBe DebateStatus.IN_PROGRESS
    }

    private fun connectAsSubscriber(
        user: User,
        debateId: String,
    ): Subscriber {
        val token = tokenProvider.issue(user).accessToken
        val session = WebSocketStompClient(StandardWebSocketClient()).connectAsync(
            "ws://localhost:$port${WebSocketConfig.ENDPOINT}",
            WebSocketHttpHeaders(),
            StompHeaders().apply { add(AUTHORIZATION, "Bearer $token") },
            object : StompSessionHandlerAdapter() {},
        ).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        sessions.add(session)
        val frames = LinkedBlockingQueue<String>()
        session.subscribe(
            DebateDestination.of(debateId),
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    (payload as? ByteArray)?.let { frames.add(String(it, Charsets.UTF_8)) }
                }
            },
        )
        return Subscriber(session, frames)
    }

    private fun awaitFrame(
        frames: BlockingQueue<String>,
        matches: (String) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            val frame = frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (matches(frame)) return
        }
        throw AssertionError("조건에 맞는 STOMP 프레임을 ${TIMEOUT_SECONDS}초 안에 받지 못했습니다")
    }

    private class Subscriber(
        val session: StompSession,
        val frames: BlockingQueue<String>,
    )

    private fun saveUser(
        prefix: String,
    ): User = userRepository.save(
        User.create(
            email = "$prefix-${UUID.randomUUID()}@pangyeori.com",
            password = "encoded-password",
            nickname = "$prefix-${UUID.randomUUID().toString().take(8)}",
        ),
    )

    private fun saveReadyDebate(
        host: User,
        guest: User,
    ): Debate {
        val debate = debateRepository.save(
            Debate.create(
                host = host,
                title = "입장 테스트 토론",
                description = null,
                hostPosition = DebatePosition.PROS,
                turnTimeSeconds = 60,
                freeDebateTimeSeconds = 60,
                inviteToken = UUID.randomUUID().toString(),
            ),
        )
        debateUserRepository.save(
            DebateUser.create(
                debate = debate,
                user = host,
                role = DebateUserRole.HOST,
                position = DebatePosition.PROS,
            ),
        )
        val guestMember = DebateUser.create(
            debate = debate,
            user = guest,
            role = DebateUserRole.GUEST,
            position = DebatePosition.CONS,
        )
        guestMember.accept()
        debateUserRepository.save(guestMember)
        debate.acceptGuest(guest)
        return debateRepository.save(debate)
    }

    @AfterEach
    fun disconnectSessions() {
        sessions.forEach { runCatching { it.disconnect() } }
        sessions.clear()
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val TIMEOUT_SECONDS = 5L
    }
}
