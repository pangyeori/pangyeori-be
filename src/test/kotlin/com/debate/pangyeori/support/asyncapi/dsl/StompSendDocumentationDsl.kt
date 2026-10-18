package com.debate.pangyeori.support.asyncapi.dsl

import com.debate.pangyeori.support.asyncapi.generator.AsyncApiDocumentStore
import com.debate.pangyeori.support.asyncapi.generator.ChannelSnippet
import com.debate.pangyeori.support.asyncapi.generator.DocumentedMessage
import com.debate.pangyeori.support.asyncapi.generator.FieldDescriptor
import com.debate.pangyeori.support.asyncapi.generator.SchemaBuilder
import com.debate.pangyeori.support.asyncapi.generator.parameterNamesFromPath
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/**
 * `send<T>(name, summary, example) { }` 블록의 리시버 (STOMP).
 *
 * 클라이언트가 서버로 보내는 메시지 하나를 문서화한다. [payload]로 실제로 보낼 값을 선언하면,
 * 그 값을 JSON으로 직렬화해 STOMP SEND 프레임으로 전송하고 동시에 예시 payload로 남긴다.
 */
@AsyncApiDocsDslMarker
class StompSendSpec<T : Any> internal constructor(
    internal val payloadType: Class<T>,
    internal val messageName: String,
    internal val componentName: String,
    internal val summary: String?,
    internal val exampleLabel: String?,
) {

    internal val fields = mutableListOf<FieldSpec>()
    internal var payloadProvider: (() -> T)? = null

    fun field(
        path: String,
        description: String,
    ) = FieldSpec(path, description).also { fields += it }

    fun payload(
        provider: () -> T,
    ) {
        payloadProvider = provider
    }
}

/**
 * `documentStompSend("resource/action", endpoint = ...) { ... }` 블록의 리시버.
 *
 * [destination]으로 클라이언트가 보낼 목적지를 선언하고, [connect]로 CONNECT 헤더와 경로 변수를 지정한 뒤,
 * [send]로 선언한 순서대로 메시지를 실제로 전송해 문서화한다. `documentStomp`(구독해서 받는 흐름)와 대응하되
 * 방향이 반대다.
 */
@AsyncApiDocsDslMarker
class StompSendDocumentationDsl internal constructor(
    private val identifier: String,
    private val baseUrl: String,
    private val endpoint: String,
    private val objectMapper: ObjectMapper,
) {

    private val schemaBuilder = SchemaBuilder(objectMapper)
    private val store = AsyncApiDocumentStore(objectMapper)

    private var channelSpec: ChannelSpec? = null
    private val parameterSpecs = mutableListOf<ParameterSpec>()
    private var connectBlock: (StompConnectSpec.() -> Unit)? = null
    private val sendSpecs = mutableListOf<StompSendSpec<*>>()

    fun destination(
        sendTo: String,
        description: String,
    ) {
        channelSpec = ChannelSpec(sendTo, PROTOCOL, description)
    }

    fun parameter(
        name: String,
        description: String,
    ) {
        parameterSpecs += ParameterSpec(name, description)
    }

    fun connect(
        block: StompConnectSpec.() -> Unit,
    ) {
        connectBlock = block
    }

    inline fun <reified T : Any> send(
        name: String? = null,
        summary: String? = null,
        example: String? = null,
        noinline block: StompSendSpec<T>.() -> Unit,
    ) {
        sendInternal(
            payloadType = T::class.java,
            messageName = name ?: T::class.simpleName ?: "Message",
            componentName = T::class.simpleName ?: "Message",
            summary = summary,
            example = example,
            block = block,
        )
    }

    @PublishedApi
    internal fun <T : Any> sendInternal(
        payloadType: Class<T>,
        messageName: String,
        componentName: String,
        summary: String?,
        example: String?,
        block: StompSendSpec<T>.() -> Unit,
    ) {
        sendSpecs += StompSendSpec(payloadType, messageName, componentName, summary, example).apply(block)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun execute() {
        val channel = channelSpec
            ?: throw IllegalStateException("destination(...) 선언이 필요합니다")
        if (sendSpecs.isEmpty()) {
            throw IllegalStateException("send<T>() { } 선언이 한 개 이상 필요합니다")
        }

        val connectSpec = StompConnectSpec()
        connectBlock?.invoke(connectSpec)
        val publishDestination = resolveDestination(
            path = channel.path,
            pathValues = connectSpec.pathValues,
        )

        val headers = StompHeaders().apply {
            connectSpec.connectHeaders.forEach { (name, value) -> add(name, value) }
        }
        val session = WebSocketStompClient(StandardWebSocketClient()).connectAsync(
            baseUrl.replaceFirst("http", "ws") + endpoint,
            WebSocketHttpHeaders(),
            headers,
            object : StompSessionHandlerAdapter() {},
        ).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val messages = try {
            (sendSpecs as List<StompSendSpec<Any>>).map { spec ->
                documentSend(
                    session = session,
                    destination = publishDestination,
                    spec = spec,
                )
            }
        } finally {
            session.disconnect()
        }

        val fragment = ChannelSnippet(
            channelPath = channel.path,
            explicitChannelName = null,
            channelDescription = channel.description,
            serverRef = AsyncApiDocumentStore.WS_SERVER_REF,
            parameters = resolveParameters(
                path = channel.path,
            ),
            messages = messages,
            action = "send",
        ).toFragment()

        store.writeFragment(identifier.replace('/', '-'), fragment)
        store.assemble()
    }

    private fun documentSend(
        session: StompSession,
        destination: String,
        spec: StompSendSpec<Any>,
    ): DocumentedMessage {
        val payload = spec.payloadProvider?.invoke()
            ?: throw IllegalStateException("payload { } 로 보낼 값을 선언해야 합니다")
        val json = objectMapper.writeValueAsString(payload)
        session.send(destination, json.toByteArray())

        val descriptors = spec.fields.map { field ->
            FieldDescriptor(
                path = field.path,
                description = field.description,
                optional = field.optional,
            )
        }
        schemaBuilder.validate(descriptors, json)

        return DocumentedMessage(
            messageKey = spec.messageName,
            componentName = spec.componentName,
            summary = spec.summary,
            payloadSchema = schemaBuilder.build(descriptors, json),
            exampleName = spec.exampleLabel ?: identifier.substringAfterLast('/'),
            examplePayload = readExample(
                json = json,
            ),
        )
    }

    private fun resolveDestination(
        path: String,
        pathValues: Map<String, String>,
    ): String {
        var resolved = path
        parameterNamesFromPath(path).forEach { name ->
            val value = pathValues[name]
                ?: throw IllegalStateException("경로 변수 '$name' 값이 connect { pathValue(...) } 로 지정되지 않았습니다")
            resolved = resolved.replace("{$name}", value)
        }
        return resolved
    }

    private fun resolveParameters(
        path: String,
    ): List<Pair<String, String>> = parameterNamesFromPath(path).map { name ->
        val description = parameterSpecs.firstOrNull { it.name == name }?.description ?: name
        name to description
    }

    @Suppress("UNCHECKED_CAST")
    private fun readExample(
        json: String,
    ): Map<String, Any> = objectMapper.readValue(json, Map::class.java) as Map<String, Any>

    companion object {
        private const val PROTOCOL = "stomp"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
    }
}
