package com.debate.pangyeori.support.asyncapi.generator

/**
 * SSE 채널 하나에 대한 스니펫.
 *
 * 한 스트림이 이름 있는 이벤트를 여러 종류 실어 나르므로, 채널 하나에 메시지 N개를 인라인으로 걸고
 * `receive` operation도 N개를 만든다. 공유 `components`는 만들지 않아 조각 간 병합 충돌이 없다.
 */
internal class SseSnippet(
    private val channelPath: String,
    private val explicitChannelName: String?,
    private val channelDescription: String,
    private val serverRef: String,
    private val parameters: List<Pair<String, String>>,
    private val messages: List<DocumentedMessage>,
) : AsyncApiSnippet {

    override fun toFragment(): Map<String, Any> {
        val channelName = explicitChannelName ?: channelNameFromPath(
            path = channelPath,
        )

        val channelNode = linkedMapOf<String, Any>(
            "address" to channelPath,
            "description" to channelDescription,
            "servers" to listOf(
                linkedMapOf<String, Any>("\$ref" to serverRef),
            ),
        )
        if (parameters.isNotEmpty()) {
            channelNode["parameters"] = parameters.associate { (name, description) ->
                name to linkedMapOf<String, Any>("description" to description)
            }
        }
        // 한 테스트가 같은 이벤트를 여러 번 receive 하면(상태 변화별 등) 예시를 나란히 담는다.
        val messagesByKey = messages.groupBy { it.messageKey }

        channelNode["messages"] = messagesByKey.mapValues { (_, group) ->
            messageNode(
                message = group.first(),
            ).toMutableMap().apply {
                this["examples"] = group.map { message ->
                    linkedMapOf<String, Any>(
                        "name" to message.exampleName,
                        "payload" to message.examplePayload,
                    )
                }
            }
        }

        val operations = messagesByKey.entries.associate { (key, group) ->
            "$channelName-$key" to receiveOperationNode(
                channelName = channelName,
                messageKey = key,
                summary = group.first().summary,
            )
        }

        return linkedMapOf(
            "channels" to linkedMapOf<String, Any>(
                channelName to channelNode,
            ),
            "operations" to operations,
        )
    }
}
