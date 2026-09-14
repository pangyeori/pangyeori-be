package com.debate.pangyeori.support.asyncapi.dsl

import com.debate.pangyeori.support.asyncapi.generator.AsyncApiDocumentStore
import com.debate.pangyeori.support.asyncapi.generator.DocumentedMessage
import com.debate.pangyeori.support.asyncapi.generator.DocumentingSseClient
import com.debate.pangyeori.support.asyncapi.generator.FieldDescriptor
import com.debate.pangyeori.support.asyncapi.generator.SchemaBuilder
import com.debate.pangyeori.support.asyncapi.generator.SseSnippet
import com.debate.pangyeori.support.asyncapi.generator.parameterNamesFromPath
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * `connect { }` 블록의 리시버.
 *
 * SSE 스트림에 붙기 전에 테스트가 실행하는 준비 코드를 담는다. 보통 여기서 JWT를 발급하고
 * 스트림 티켓 발급 API를 호출한 뒤, 받은 티켓을 [query]로, 경로 변수 값을 [pathValue]로 넘긴다.
 */
@AsyncApiDocsDslMarker
class SseConnectSpec internal constructor() {

    internal val queryParams = linkedMapOf<String, String>()
    internal val pathValues = linkedMapOf<String, String>()
    internal val requestHeaders = linkedMapOf<String, String>()

    fun query(
        name: String,
        value: String,
    ) {
        queryParams[name] = value
    }

    fun pathValue(
        name: String,
        value: String,
    ) {
        pathValues[name] = value
    }

    fun header(
        name: String,
        value: String,
    ) {
        requestHeaders[name] = value
    }
}

/**
 * `receive<T>(event, summary) { }` 블록의 리시버.
 *
 * 이 스트림이 보내는 이름 있는 이벤트 하나를 문서화한다. 필드를 [field]로 선언하고,
 * 서버 발행을 유도할 코드가 필요하면 [trigger]에 담고, 수신 payload 값을 [verify]로 검증한다.
 * 구독 직후 서버가 알아서 푸시하는 스냅샷 같은 이벤트는 [trigger] 없이 선언하면 된다.
 */
@AsyncApiDocsDslMarker
class SseReceiveSpec<T : Any> internal constructor(
    internal val payloadType: Class<T>,
    internal val eventName: String,
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
 * `documentSse("resource/action") { ... }` 블록의 리시버.
 *
 * `channel(...)`과 `receive<T>(event) { }` 한 개 이상이 필수다. [execute]가 준비 → SSE 연결 →
 * (이벤트별 트리거) → 수신 → verify → field 대조 → 스니펫 생성/조립을 순서대로 수행하며,
 * `verify`나 field 대조가 실패하면 스니펫은 생성되지 않는다.
 */
@AsyncApiDocsDslMarker
class SseDocumentationDsl internal constructor(
    private val identifier: String,
    private val baseUrl: String,
    private val objectMapper: ObjectMapper,
) {

    private val schemaBuilder = SchemaBuilder(objectMapper)
    private val store = AsyncApiDocumentStore(objectMapper)

    private var channelSpec: ChannelSpec? = null
    private val parameterSpecs = mutableListOf<ParameterSpec>()
    private var connectBlock: (SseConnectSpec.() -> Unit)? = null
    private val receiveSpecs = mutableListOf<SseReceiveSpec<*>>()

    fun channel(
        path: String,
        protocol: String,
        description: String,
        name: String? = null,
    ) {
        channelSpec = ChannelSpec(path, protocol, description, name)
    }

    fun parameter(
        name: String,
        description: String,
    ) {
        parameterSpecs += ParameterSpec(name, description)
    }

    fun connect(
        block: SseConnectSpec.() -> Unit,
    ) {
        connectBlock = block
    }

    inline fun <reified T : Any> receive(
        event: String,
        summary: String? = null,
        example: String? = null,
        noinline block: SseReceiveSpec<T>.() -> Unit,
    ) {
        receiveInternal(
            payloadType = T::class.java,
            eventName = event,
            componentName = T::class.simpleName ?: "Event",
            summary = summary,
            example = example,
            block = block,
        )
    }

    @PublishedApi
    internal fun <T : Any> receiveInternal(
        payloadType: Class<T>,
        eventName: String,
        componentName: String,
        summary: String?,
        example: String?,
        block: SseReceiveSpec<T>.() -> Unit,
    ) {
        receiveSpecs += SseReceiveSpec(payloadType, eventName, componentName, summary, example).apply(block)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun execute() {
        val channel = channelSpec
            ?: throw IllegalStateException("channel(...) 선언이 필요합니다")
        if (receiveSpecs.isEmpty()) {
            throw IllegalStateException("receive<T>(event) { } 선언이 한 개 이상 필요합니다")
        }

        val connectSpec = SseConnectSpec()
        connectBlock?.invoke(connectSpec)
        val url = buildUrl(
            path = channel.path,
            pathValues = connectSpec.pathValues,
            queryParams = connectSpec.queryParams,
        )

        val client = DocumentingSseClient(connectSpec.requestHeaders)
        val messages = try {
            client.connect(url)
            (receiveSpecs as List<SseReceiveSpec<Any>>).map { spec ->
                documentEvent(
                    client = client,
                    spec = spec,
                )
            }
        } finally {
            client.close()
        }

        val fragment = SseSnippet(
            channelPath = channel.path,
            explicitChannelName = channel.name,
            channelDescription = channel.description,
            serverRef = AsyncApiDocumentStore.SERVER_REF,
            parameters = resolveParameters(
                path = channel.path,
            ),
            messages = messages,
        ).toFragment()

        store.writeFragment(identifier.replace('/', '-'), fragment)
        store.assemble()
    }

    private fun documentEvent(
        client: DocumentingSseClient,
        spec: SseReceiveSpec<Any>,
    ): DocumentedMessage {
        spec.triggerBlock?.invoke()
        val event = client.nextEventNamed(
            name = spec.eventName,
            timeout = EVENT_TIMEOUT,
        )

        val payload = objectMapper.readValue(event.data, spec.payloadType)
        spec.verifyBlock?.invoke(payload)

        val descriptors = spec.fields.map { field ->
            FieldDescriptor(
                path = field.path,
                description = field.description,
                optional = field.optional,
            )
        }
        schemaBuilder.validate(descriptors, event.data)

        return DocumentedMessage(
            messageKey = spec.eventName,
            componentName = spec.componentName,
            summary = spec.summary,
            payloadSchema = schemaBuilder.build(descriptors, event.data),
            exampleName = spec.exampleLabel ?: identifier.substringAfterLast('/'),
            examplePayload = readExample(
                json = event.data,
            ),
        )
    }

    private fun buildUrl(
        path: String,
        pathValues: Map<String, String>,
        queryParams: Map<String, String>,
    ): String {
        var resolved = path
        parameterNamesFromPath(path).forEach { name ->
            val value = pathValues[name]
                ?: throw IllegalStateException("경로 변수 '$name' 값이 connect { pathValue(...) } 로 지정되지 않았습니다")
            resolved = resolved.replace("{$name}", value)
        }
        val query = if (queryParams.isEmpty()) {
            ""
        } else {
            "?" + queryParams.entries.joinToString("&") { (k, v) -> "$k=$v" }
        }
        return baseUrl + resolved + query
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
        private val EVENT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
