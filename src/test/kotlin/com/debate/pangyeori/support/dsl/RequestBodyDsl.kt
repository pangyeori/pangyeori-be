package com.debate.pangyeori.support.dsl

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

/**
 * JSON 요청 바디를 필드 단위로 작성하는 DSL.
 *
 * `field()`를 호출할 때마다 실제 요청 바디에 포함될 값과 문서에 기록될 설명이 함께 등록된다.
 * 중첩 필드는 점 표기법(`profile.city`), 배열 항목은 `roles[]` 형식으로 지정한다.
 *
 * 완성된 JSON 문자열을 직접 넘기고 싶을 때는 `rawJson()`을 사용할 수 있다.
 * 단, `rawJson()`을 사용하더라도 문서화가 필요한 필드는 `field()`로 별도로 선언해야 한다.
 */
@RestDocsDslMarker
class RequestBodyDsl {
    private val fieldBuilders = mutableListOf<RequestFieldBuilder>()
    private val bodyMap = LinkedHashMap<String, Any?>()
    private var rawJsonBody: String? = null

    fun field(
        path: String,
        value: Any?,
        description: String,
        type: JsonFieldType = value.inferJsonFieldType(),
    ): RequestFieldBuilder {
        setNestedValue(bodyMap, path.trimEnd('[', ']'), value)
        return RequestFieldBuilder(path, description, optional = false, type)
            .also { fieldBuilders += it }
    }

    /**
     * 완성된 JSON 문자열을 요청 바디로 직접 지정한다.
     *
     * `field()`로 값을 하나씩 쌓는 대신 이미 직렬화된 JSON이 있을 때 사용한다.
     * 호출하면 `field()`로 누적된 바디 맵은 무시되고 이 JSON 문자열만 전송된다.
     */
    fun rawJson(json: String) {
        rawJsonBody = json
    }

    internal fun toJson(): String = rawJsonBody ?: bodyMap.toJsonString()

    internal fun descriptors(): List<FieldDescriptor> = fieldBuilders.map { it.build() }

    private fun setNestedValue(map: MutableMap<String, Any?>, path: String, value: Any?) {
        val dotIndex = path.indexOf('.')
        if (dotIndex == -1) {
            map[path] = value
            return
        }
        val key = path.substring(0, dotIndex)
        val rest = path.substring(dotIndex + 1)

        @Suppress("UNCHECKED_CAST")
        val child = map.getOrPut(key) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>
        setNestedValue(child, rest, value)
    }
}

private fun Map<String, Any?>.toJsonString(): String = buildString {
    append('{')
    entries.forEachIndexed { i, (k, v) ->
        if (i > 0) append(',')
        append('"').append(k).append('"').append(':')
        append(v.toJsonValue())
    }
    append('}')
}

private fun Any?.toJsonValue(): String = when (this) {
    null -> "null"
    is Boolean -> toString()
    is Number -> toString()
    is String -> "\"${escapeJson()}\""
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (this as Map<String, Any?>).toJsonString()
    is Iterable<*> -> "[${joinToString(",") { it.toJsonValue() }}]"
    is Array<*> -> "[${joinToString(",") { it.toJsonValue() }}]"
    else -> "\"${toString().escapeJson()}\""
}

private fun String.escapeJson(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

internal fun Any?.inferJsonFieldType(): JsonFieldType = when (this) {
    null -> JsonFieldType.NULL
    is Boolean -> JsonFieldType.BOOLEAN
    is Number -> JsonFieldType.NUMBER
    is String -> JsonFieldType.STRING
    is Enum<*> -> JsonFieldType.STRING
    is Map<*, *> -> JsonFieldType.OBJECT
    is Iterable<*>, is Array<*> -> JsonFieldType.ARRAY
    else -> JsonFieldType.VARIES
}

/**
 * 요청 바디 필드 하나를 설정하는 빌더.
 *
 * `optional()`을 호출하면 실제 요청 바디에 해당 필드가 없어도 REST Docs 검증이 통과된다.
 * `type()`으로 필드 타입을 명시하면 생성된 스니펫 문서에 타입 정보가 포함된다.
 * 타입을 지정하지 않으면 `value`에서 자동 추론한다.
 */
class RequestFieldBuilder internal constructor(
    private val path: String,
    private val description: String,
    private var optional: Boolean,
    private var type: JsonFieldType,
) {
    fun optional(): RequestFieldBuilder = apply { optional = true }
    fun type(jsonFieldType: JsonFieldType): RequestFieldBuilder = apply { type = jsonFieldType }

    internal fun build(): FieldDescriptor =
        fieldWithPath(path).description(description).type(type)
            .let { if (optional) it.optional() else it }
}
