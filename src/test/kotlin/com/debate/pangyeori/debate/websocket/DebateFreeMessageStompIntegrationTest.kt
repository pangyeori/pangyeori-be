package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.auth.token.TokenProvider
import com.debate.pangyeori.config.WebSocketConfig
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
import com.debate.pangyeori.debate.dto.request.DebateFreeMessageSubmitRequest
import com.debate.pangyeori.debate.repository.DebateRepository
import com.debate.pangyeori.debate.repository.DebateUserRepository
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

class DebateFreeMessageStompIntegrationTest : AsyncApiDocsTest() {

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
    fun `자유 토론 메시지 전송을 문서화한다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!
        val hostToken = tokenProvider.issue(host).accessToken

        documentStompSend("debates/freeMessages", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                sendTo = "${WebSocketConfig.PUBLISH_PREFIX}/debates/{debateId}/free-messages",
                description = "자유 토론 메시지 전송. 먼저 STOMP WebSocket으로 연결한 뒤 이 목적지로 보낸다. " +
                    "성공하면 같은 토론방을 구독 중인 모든 참가자에게 FREE_MESSAGE_SENT 이벤트로 전달된다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $hostToken")
                pathValue("debateId", debateId)
            }
            send<DebateFreeMessageSubmitRequest>(
                summary = "자유 토론 메시지를 보낸다",
                example = "자유 토론 메시지 전송",
            ) {
                payload { DebateFreeMessageSubmitRequest(content = "안녕하세요") }
                field("content", "메시지 내용 (최대 500자)")
            }
        }
    }

    @Test
    fun `자유 토론 단계에서 메시지를 보내면 구독자 전원에게 실시간으로 전달된다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/free-messages",
            """{"content":"안녕하세요"}""".toByteArray(),
        )

        val frame = awaitFrame(guestSubscriber.frames) { it.contains("FREE_MESSAGE_SENT") }
        frame.contains("\"content\":\"안녕하세요\"") shouldBe true
        frame.contains("\"role\":\"HOST\"") shouldBe true
    }

    @Test
    fun `자유 토론 단계가 아닐 때 메시지를 보내면 개인 에러 큐로 오류를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val errorFrames = subscribeToErrors(hostSubscriber.session)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/free-messages",
            """{"content":"아직 자유 토론이 아닙니다"}""".toByteArray(),
        )

        val frame = awaitFrame(errorFrames) { it.contains("DEBATE_NOT_FREE_STAGE") }
        frame.contains("\"code\":\"DEBATE_NOT_FREE_STAGE\"") shouldBe true
    }

    private fun connectAndSubscribe(
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

    private fun subscribeToErrors(
        session: StompSession,
    ): BlockingQueue<String> {
        val frames = LinkedBlockingQueue<String>()
        session.subscribe(
            "/user${WebSocketConfig.QUEUE_PREFIX}/errors",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = ByteArray::class.java

                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    (payload as? ByteArray)?.let { frames.add(String(it, Charsets.UTF_8)) }
                }
            },
        )
        return frames
    }

    private fun awaitFrame(
        frames: BlockingQueue<String>,
        matches: (String) -> Boolean,
    ): String {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (System.currentTimeMillis() < deadline) {
            val frame = frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (matches(frame)) return frame
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
                title = "자유 토론 WS 테스트",
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

    private fun saveFreeStageDebate(
        host: User,
        guest: User,
    ): Debate {
        val debate = saveReadyDebate(host = host, guest = guest)
        debate.start()
        debate.advanceStage(next = DebateStage.FREE)
        return debateRepository.saveAndFlush(debate)
    }

    @AfterEach
    fun disconnectSessions() {
        sessions.forEach { runCatching { it.disconnect() } }
        sessions.clear()
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"
        private const val TIMEOUT_SECONDS = 10L
    }
}
