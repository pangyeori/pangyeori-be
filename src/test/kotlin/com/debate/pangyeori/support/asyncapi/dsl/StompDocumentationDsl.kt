package com.debate.pangyeori.support.asyncapi.dsl

import com.debate.pangyeori.support.asyncapi.generator.AsyncApiDocumentStore
import com.debate.pangyeori.support.asyncapi.generator.ChannelSnippet
import com.debate.pangyeori.support.asyncapi.generator.DocumentedMessage
import com.debate.pangyeori.support.asyncapi.generator.DocumentingStompClient
import com.debate.pangyeori.support.asyncapi.generator.FieldDescriptor
import com.debate.pangyeori.support.asyncapi.generator.SchemaBuilder
import com.debate.pangyeori.support.asyncapi.generator.parameterNamesFromPath
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * `connect { }` 블록의 리시버 (STOMP).
 *
 * CONNECT 프레임에 실을 헤더(예: `Authorization`)와 목적지 경로 변수 값([pathValue])을 지정한다.
 */
@AsyncApiDocsDslMarker
class StompConnectSpec internal constructor() {

    internal val connectHeaders = linkedMapOf<String, String>()
    internal val pathValues = linkedMapOf<String, String>()

    fun header(
        name: String,
        value: String,
    ) {
        connectHeaders[name] = value
    }

    fun pathValue(
        name: String,
        value: String,
    ) {
        pathValues[name] = value
    }
}

/**
 * `receive<T>(name, summary, example) { }` 블록의 리시버 (STOMP).
 *
 * 구독한 목적지로 도착하는 메시지 하나를 문서화한다. [trigger]는 메시지 수신 전에 실행되어 서버 발행을 유도하고,
 * [verify]는 역직렬화된 payload를 검증한다. 같은 타입의 메시지를 여러 번 받으면 [example]로 예시를 구분한다.
 */
@AsyncApiDocsDslMarker
class StompReceiveSpec<T : Any> internal constructor(
    internal val payloadType: Class<T>,
    internal val messageName: String,
    internal val componentName: String,
    internal val summary: String?,
    internal val exampleLabel: String?,
) {

    internal val fields = mutableListOf<FieldSpec>()
    internal var triggerBlock: (() -> Unit)? = null
    internal var verifyBlock: ((T) -> Unit)? = null

    fun field(
        path: String,
        description: String,
    ) = FieldSpec(path, description).also { fields += it }

    fun trigger(
        block: () -> Unit,
    ) {
        triggerBlock = block
    }

    fun verify(
        block: (T) -> Unit,
    ) {
        verifyBlock = block
    }
}

/**
 * `documentStomp("resource/action", endpoint = ...) { ... }` 블록의 리시버.
 *
 * [destination]으로 구독 목적지를 선언하고, [connect]로 CONNECT 헤더와 경로 변수를 지정한 뒤,
 * [receive]를 선언한 순서대로 메시지를 받아 문서화한다. 구독은 [execute]에서 한 번만 맺고,
 * 모든 receive가 같은 세션을 공유한다.
 */
@AsyncApiDocsDslMarker
class StompDocumentationDsl internal constructor(
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
    private val receiveSpecs = mutableListOf<StompReceiveSpec<*>>()

    fun destination(
        subscribeTo: String,
        description: String,
    ) {
        channelSpec = ChannelSpec(subscribeTo, PROTOCOL, description)
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

    inline fun <reified T : Any> receive(
        name: String? = null,
        summary: String? = null,
        example: String? = null,
        noinline block: StompReceiveSpec<T>.() -> Unit,
    ) {
        receiveInternal(
            payloadType = T::class.java,
            messageName = name ?: T::class.simpleName ?: "Message",
            componentName = T::class.simpleName ?: "Message",
            summary = summary,
            example = example,
            block = block,
        )
    }

    @PublishedApi
    internal fun <T : Any> receiveInternal(
        payloadType: Class<T>,
        messageName: String,
        componentName: String,
        summary: String?,
        example: String?,
        block: StompReceiveSpec<T>.() -> Unit,
    ) {
        receiveSpecs += StompReceiveSpec(payloadType, messageName, componentName, summary, example).apply(block)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun execute() {
        val channel = channelSpec
            ?: throw IllegalStateException("destination(...) 선언이 필요합니다")
        if (receiveSpecs.isEmpty()) {
            throw IllegalStateException("receive<T>() { } 선언이 한 개 이상 필요합니다")
        }

        val connectSpec = StompConnectSpec()
        connectBlock?.invoke(connectSpec)
        val subscribeDestination = resolveDestination(
            path = channel.path,
            pathValues = connectSpec.pathValues,
        )

        val client = DocumentingStompClient(
            url = baseUrl.replaceFirst("http", "ws") + endpoint,
            connectHeaders = connectSpec.connectHeaders,
            destination = subscribeDestination,
        )
        val messages = try {
            client.connect()
            (receiveSpecs as List<StompReceiveSpec<Any>>).map { spec ->
                documentReceive(
                    client = client,
                    spec = spec,
                )
            }
        } finally {
            client.close()
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
        ).toFragment()

        store.writeFragment(identifier.replace('/', '-'), fragment)
        store.assemble()
    }

    private fun documentReceive(
        client: DocumentingStompClient,
        spec: StompReceiveSpec<Any>,
    ): DocumentedMessage {
        spec.triggerBlock?.invoke()
        val frame = client.nextFrame(RECEIVE_TIMEOUT)

        val payload = objectMapper.readValue(frame, spec.payloadType)
        spec.verifyBlock?.invoke(payload)

        val descriptors = spec.fields.map { field ->
            FieldDescriptor(
                path = field.path,
                description = field.description,
                optional = field.optional,
            )
        }
        schemaBuilder.validate(descriptors, frame)

        return DocumentedMessage(
            messageKey = spec.messageName,
            componentName = spec.componentName,
            summary = spec.summary,
            payloadSchema = schemaBuilder.build(descriptors, frame),
            exampleName = spec.exampleLabel ?: identifier.substringAfterLast('/'),
            examplePayload = readExample(
                json = frame,
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
        private val RECEIVE_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
