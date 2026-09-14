package com.debate.pangyeori.support.asyncapi.generator

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

/**
 * 문서화 중 실제 SSE 스트림을 수신하는 클라이언트.
 *
 * `java.net.http.HttpClient`만 쓴다 (WebFlux 의존성 없음). [connect]가 리턴하면 스트림이 열린 상태이고,
 * 가상 스레드에서 `text/event-stream`의 `event:` / `id:` / `data:` 라인을 읽어 [SseEvent]로 모은다.
 * `:`로 시작하는 주석 라인(heartbeat)은 무시한다.
 */
internal class DocumentingSseClient(
    private val headers: Map<String, String>,
) {

    private val httpClient = HttpClient.newHttpClient()
    private val events = LinkedBlockingQueue<SseEvent>()
    private val holdback = mutableListOf<SseEvent>()

    @Volatile
    private var closed = false
    private var lineStream: Stream<String>? = null
    private var readerThread: Thread? = null

    fun connect(
        url: String,
    ) {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "text/event-stream")
            .GET()
        headers.forEach { (name, value) -> builder.header(name, value) }

        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofLines())
        if (response.statusCode() != HTTP_OK) {
            throw IllegalStateException("SSE 연결에 실패했습니다: HTTP ${response.statusCode()}")
        }

        val stream = response.body()
        lineStream = stream
        readerThread = Thread.ofVirtual()
            .name("documenting-sse-reader")
            .start { readLoop(stream) }
    }

    // 다음에 도착하는 이벤트를 timeout까지 기다린다. 이름은 따지지 않는다.
    fun nextEvent(
        timeout: Duration,
    ): SseEvent {
        if (holdback.isNotEmpty()) {
            return holdback.removeAt(0)
        }
        return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
            ?: throw IllegalStateException("${timeout.toMillis()}ms 안에 SSE 이벤트를 수신하지 못했습니다")
    }

    // 이름이 [name]인 이벤트를 timeout까지 기다린다. 그 사이 도착한 다른 이벤트는 보관해 두었다가
    // 이후 호출에서 순서대로 돌려준다.
    fun nextEventNamed(
        name: String,
        timeout: Duration,
    ): SseEvent {
        holdback.firstOrNull { it.name == name }?.let {
            holdback.remove(it)
            return it
        }

        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val waitMillis = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
            val event = events.poll(waitMillis, TimeUnit.MILLISECONDS) ?: break
            if (event.name == name) {
                return event
            }
            holdback.add(event)
        }
        throw IllegalStateException("${timeout.toMillis()}ms 안에 '$name' SSE 이벤트를 수신하지 못했습니다")
    }

    fun close() {
        closed = true
        readerThread?.interrupt()
        try {
            lineStream?.close()
        } catch (ignored: Exception) {
            // 스트림 종료 중 발생하는 예외는 무시한다
        }
        // 서버가 곧바로 연결 끊김을 인지하도록 진행 중인 교환을 강제 종료한다.
        httpClient.shutdownNow()
    }

    private fun readLoop(
        stream: Stream<String>,
    ) {
        var eventName: String? = null
        var eventId: String? = null
        val data = StringBuilder()

        try {
            val iterator = stream.iterator()
            while (!closed && iterator.hasNext()) {
                val line = iterator.next()
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) {
                            events.add(
                                SseEvent(
                                    name = eventName ?: DEFAULT_EVENT_NAME,
                                    id = eventId,
                                    data = data.toString().trimEnd('\n'),
                                ),
                            )
                        }
                        eventName = null
                        eventId = null
                        data.setLength(0)
                    }

                    line.startsWith(FIELD_COMMENT) -> Unit

                    line.startsWith(FIELD_EVENT) -> eventName = line.removePrefix(FIELD_EVENT).trim()

                    line.startsWith(FIELD_ID) -> eventId = line.removePrefix(FIELD_ID).trim()

                    line.startsWith(FIELD_DATA) -> data.append(line.removePrefix(FIELD_DATA).trim()).append('\n')

                    else -> Unit
                }
            }
        } catch (ignored: Exception) {
            // 연결이 닫히면 읽기 루프를 종료한다
        }
    }

    companion object {
        private const val HTTP_OK = 200
        private const val DEFAULT_EVENT_NAME = "message"
        private const val FIELD_EVENT = "event:"
        private const val FIELD_ID = "id:"
        private const val FIELD_DATA = "data:"
        private const val FIELD_COMMENT = ":"
    }
}

/**
 * 수신한 SSE 이벤트 한 건.
 */
internal data class SseEvent(
    val name: String,
    val id: String?,
    val data: String,
)
