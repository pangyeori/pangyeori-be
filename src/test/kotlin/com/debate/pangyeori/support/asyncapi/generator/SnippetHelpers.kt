package com.debate.pangyeori.support.asyncapi.generator

/**
 * 한 방향(여기서는 서버가 보내는 receive)에 대한 문서화된 메시지.
 *
 * @param payloadSchema [SchemaBuilder.build] 결과
 * @param exampleName 이 예시를 구분하는 이름. 같은 메시지를 여러 문서 테스트가 다루면 예시가 나란히 쌓인다
 * @param examplePayload 테스트에서 실제로 수신한 payload
 */
internal data class DocumentedMessage(
    val messageKey: String,
    val componentName: String,
    val summary: String?,
    val payloadSchema: Map<String, Any>,
    val exampleName: String,
    val examplePayload: Map<String, Any>,
)

// 채널 주소 세그먼트를 camelCase로 이어 채널 이름을 만든다.
// /api/v1/debates/{debateId}/status/stream -> apiV1DebatesDebateIdStatusStream
internal fun channelNameFromPath(
    path: String,
): String {
    val segments = path.trim('/').split(Regex("[/_{}-]")).filter { it.isNotEmpty() }
    if (segments.isEmpty()) {
        return "channel"
    }
    return segments.first().replaceFirstChar { it.lowercaseChar() } +
        segments.drop(1).joinToString("") { segment ->
            segment.replaceFirstChar { it.uppercaseChar() }
        }
}

// 채널 주소의 {name} 치환자 목록을 뽑는다.
internal fun parameterNamesFromPath(
    path: String,
): List<String> = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toList()

internal fun receiveOperationNode(
    channelName: String,
    messageKey: String,
    summary: String?,
): Map<String, Any> {
    val node = linkedMapOf<String, Any>(
        "action" to "receive",
        "channel" to linkedMapOf<String, Any>(
            "\$ref" to "#/channels/$channelName",
        ),
        "messages" to listOf(
            linkedMapOf<String, Any>(
                "\$ref" to "#/channels/$channelName/messages/$messageKey",
            ),
        ),
    )
    summary?.let { node["summary"] = it }
    return node
}

internal fun messageNode(
    message: DocumentedMessage,
): Map<String, Any> {
    val node = linkedMapOf<String, Any>(
        "name" to message.componentName,
        "contentType" to "application/json",
    )
    message.summary?.let { node["summary"] = it }
    node["payload"] = message.payloadSchema
    node["examples"] = listOf(
        linkedMapOf<String, Any>(
            "name" to message.exampleName,
            "payload" to message.examplePayload,
        ),
    )
    return node
}
