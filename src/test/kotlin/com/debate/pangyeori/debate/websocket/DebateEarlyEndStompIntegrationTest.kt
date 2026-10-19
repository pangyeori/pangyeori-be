package com.debate.pangyeori.debate.websocket

import com.debate.pangyeori.auth.token.TokenProvider
import com.debate.pangyeori.config.WebSocketConfig
import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.debate.domain.DebateUser
import com.debate.pangyeori.debate.domain.enums.DebatePosition
import com.debate.pangyeori.debate.domain.enums.DebateStage
import com.debate.pangyeori.debate.domain.enums.DebateUserRole
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

class DebateEarlyEndStompIntegrationTest : AsyncApiDocsTest() {

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
    fun `조기 종료 제안 전송을 문서화한다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!
        val hostToken = tokenProvider.issue(host).accessToken

        documentStompSend("debates/earlyEndPropose", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                sendTo = "${WebSocketConfig.PUBLISH_PREFIX}/debates/{debateId}/early-end/propose",
                description = "자유 토론 조기 종료 제안. 성공하면 같은 토론방을 구독 중인 모든 참가자에게 " +
                    "EARLY_END_PROPOSED 이벤트로 전달된다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $hostToken")
                pathValue("debateId", debateId)
            }
            send<Unit>(
                name = "EarlyEndPropose",
                summary = "조기 종료를 제안한다",
                example = "조기 종료 제안",
            ) {
                payload { }
            }
        }
    }

    @Test
    fun `조기 종료 수락 전송을 문서화한다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debate = saveFreeStageDebate(host = host, guest = guest)
        connectAndSubscribe(host, debate.id!!).session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/${debate.id}/early-end/propose",
            ByteArray(0),
        )
        val guestToken = tokenProvider.issue(guest).accessToken

        documentStompSend("debates/earlyEndAccept", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                sendTo = "${WebSocketConfig.PUBLISH_PREFIX}/debates/{debateId}/early-end/accept",
                description = "자유 토론 조기 종료 제안 수락. 성공하면 같은 토론방을 구독 중인 모든 참가자에게 " +
                    "EARLY_END_ACCEPTED 이벤트가, 이어서 최종 판결 단계로의 STAGE_CHANGED 이벤트가 전달된다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $guestToken")
                pathValue("debateId", debate.id!!)
            }
            send<Unit>(
                name = "EarlyEndAccept",
                summary = "조기 종료 제안을 수락한다",
                example = "조기 종료 제안 수락",
            ) {
                payload { }
            }
        }
    }

    @Test
    fun `조기 종료 거절 전송을 문서화한다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debate = saveFreeStageDebate(host = host, guest = guest)
        connectAndSubscribe(host, debate.id!!).session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/${debate.id}/early-end/propose",
            ByteArray(0),
        )
        val guestToken = tokenProvider.issue(guest).accessToken

        documentStompSend("debates/earlyEndDecline", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                sendTo = "${WebSocketConfig.PUBLISH_PREFIX}/debates/{debateId}/early-end/decline",
                description = "자유 토론 조기 종료 제안 거절. 성공하면 같은 토론방을 구독 중인 모든 참가자에게 " +
                    "EARLY_END_DECLINED 이벤트로 전달된다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $guestToken")
                pathValue("debateId", debate.id!!)
            }
            send<Unit>(
                name = "EarlyEndDecline",
                summary = "조기 종료 제안을 거절한다",
                example = "조기 종료 제안 거절",
            ) {
                payload { }
            }
        }
    }

    @Test
    fun `조기 종료 취소 전송을 문서화한다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debate = saveFreeStageDebate(host = host, guest = guest)
        val hostToken = tokenProvider.issue(host).accessToken
        connectAndSubscribe(host, debate.id!!).session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/${debate.id}/early-end/propose",
            ByteArray(0),
        )

        documentStompSend("debates/earlyEndCancel", endpoint = WebSocketConfig.ENDPOINT) {
            destination(
                sendTo = "${WebSocketConfig.PUBLISH_PREFIX}/debates/{debateId}/early-end/cancel",
                description = "자유 토론 조기 종료 제안 취소. 상대가 응답하기 전까지 제안자 본인만 취소할 수 있다. " +
                    "성공하면 같은 토론방을 구독 중인 모든 참가자에게 EARLY_END_CANCELLED 이벤트로 전달된다",
            )
            parameter("debateId", "토론방 ID")
            connect {
                header(AUTHORIZATION, "Bearer $hostToken")
                pathValue("debateId", debate.id!!)
            }
            send<Unit>(
                name = "EarlyEndCancel",
                summary = "조기 종료 제안을 취소한다",
                example = "조기 종료 제안 취소",
            ) {
                payload { }
            }
        }
    }

    @Test
    fun `조기 종료를 제안하면 상대방에게 실시간으로 전달된다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )

        val frame = awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }
        frame.contains("\"role\":\"HOST\"") shouldBe true
    }

    @Test
    fun `제안을 수락하면 합의 이벤트와 최종 판결 단계 전환 이벤트가 모두 전달된다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )
        awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }

        guestSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/accept",
            ByteArray(0),
        )

        val acceptedFrame = awaitFrame(hostSubscriber.frames) { it.contains("EARLY_END_ACCEPTED") }
        acceptedFrame.contains("\"role\":\"GUEST\"") shouldBe true
        val stageChangedFrame = awaitFrame(hostSubscriber.frames) { it.contains("STAGE_CHANGED") }
        stageChangedFrame.contains("\"stage\":\"FINAL_VERDICT\"") shouldBe true
        debateRepository.findById(debateId).orElseThrow().currentStage shouldBe DebateStage.FINAL_VERDICT
    }

    @Test
    fun `제안을 거절하면 거절 이벤트가 전달되고 토론은 계속된다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )
        awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }

        guestSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/decline",
            ByteArray(0),
        )

        val frame = awaitFrame(hostSubscriber.frames) { it.contains("EARLY_END_DECLINED") }
        frame.contains("\"role\":\"GUEST\"") shouldBe true
        debateRepository.findById(debateId).orElseThrow().currentStage shouldBe DebateStage.FREE
    }

    @Test
    fun `제안자가 응답 전에 제안을 취소하면 취소 이벤트가 전달된다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )
        awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/cancel",
            ByteArray(0),
        )

        val frame = awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_CANCELLED") }
        frame.contains("\"role\":\"HOST\"") shouldBe true
    }

    @Test
    fun `자유 토론 단계가 아닐 때 제안하면 개인 에러 큐로 오류를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveReadyDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val errorFrames = subscribeToErrors(hostSubscriber.session)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )

        val frame = awaitFrame(errorFrames) { it.contains("DEBATE_NOT_FREE_STAGE") }
        frame.contains("\"code\":\"DEBATE_NOT_FREE_STAGE\"") shouldBe true
    }

    @Test
    fun `이미 진행 중인 제안이 있을 때 다시 제안하면 개인 에러 큐로 오류를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)
        val errorFrames = subscribeToErrors(guestSubscriber.session)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )
        awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }

        guestSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )

        val frame = awaitFrame(errorFrames) { it.contains("DEBATE_EARLY_END_ALREADY_PROPOSED") }
        frame.contains("\"code\":\"DEBATE_EARLY_END_ALREADY_PROPOSED\"") shouldBe true
    }

    @Test
    fun `본인이 제안한 조기 종료를 수락하려 하면 개인 에러 큐로 오류를 받는다`() {
        val host = saveUser("host")
        val guest = saveUser("guest")
        val debateId = saveFreeStageDebate(host = host, guest = guest).id!!

        val hostSubscriber = connectAndSubscribe(host, debateId)
        val guestSubscriber = connectAndSubscribe(guest, debateId)
        val errorFrames = subscribeToErrors(hostSubscriber.session)

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/propose",
            ByteArray(0),
        )
        awaitFrame(guestSubscriber.frames) { it.contains("EARLY_END_PROPOSED") }

        hostSubscriber.session.send(
            "${WebSocketConfig.PUBLISH_PREFIX}/debates/$debateId/early-end/accept",
            ByteArray(0),
        )

        val frame = awaitFrame(errorFrames) { it.contains("DEBATE_EARLY_END_SELF_RESPONSE_NOT_ALLOWED") }
        frame.contains("\"code\":\"DEBATE_EARLY_END_SELF_RESPONSE_NOT_ALLOWED\"") shouldBe true
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
                title = "조기 종료 WS 테스트",
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
