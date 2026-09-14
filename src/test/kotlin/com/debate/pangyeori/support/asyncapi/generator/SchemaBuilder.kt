package com.debate.pangyeori.support.asyncapi.generator

import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 문서화할 payload 필드 하나의 정보.
 *
 * 타입은 캡처된 실제 값에서 추론한다. [optional]이면 payload에 없어도 검증을 통과한다.
 */
internal data class FieldDescriptor(
    val path: String,
    val description: String,
    val optional: Boolean,
)

/**
 * 선언한 필드 목록과 실제 캡처된 payload 를 대조(validate)하고,
 * 통과하면 AsyncAPI payload 스키마를 만든다(build).
 *
 * Phase 1은 평평한 객체 payload만 다룬다. 중첩 객체나 배열 요소 스키마는 이후 과제다.
 */
internal class SchemaBuilder(
    private val objectMapper: ObjectMapper,
) {

    fun validate(
        fields: List<FieldDescriptor>,
        payloadJson: String,
    ) {
        val root = readObject(
            payloadJson = payloadJson,
        )

        val declaredPaths = fields.map { it.path }.toSet()
        val actualPaths = root.keys

        val undeclared = actualPaths - declaredPaths
        if (undeclared.isNotEmpty()) {
            throw AssertionError("문서화되지 않은 payload 필드가 있습니다: $undeclared. field(\"...\") 로 선언하세요")
        }

        val missing = fields.filterNot { it.optional }.map { it.path }.filterNot { it in actualPaths }
        if (missing.isNotEmpty()) {
            throw AssertionError("선언한 필수 필드가 payload 에 없습니다: $missing")
        }
    }

    fun build(
        fields: List<FieldDescriptor>,
        payloadJson: String,
    ): Map<String, Any> {
        val root = readObject(
            payloadJson = payloadJson,
        )

        val properties = LinkedHashMap<String, Any>()
        fields.forEach { field ->
            properties[field.path] = linkedMapOf<String, Any>(
                "type" to inferType(
                    value = root[field.path],
                ),
                "description" to field.description,
            )
        }

        val schema = LinkedHashMap<String, Any>()
        schema["type"] = "object"
        val required = fields.filterNot { it.optional }.map { it.path }
        if (required.isNotEmpty()) {
            schema["required"] = required
        }
        schema["properties"] = properties
        return schema
    }

    @Suppress("UNCHECKED_CAST")
    private fun readObject(
        payloadJson: String,
    ): Map<String, Any?> {
        val parsed = objectMapper.readValue(payloadJson, Map::class.java)
        return parsed as? Map<String, Any?>
            ?: throw AssertionError("객체 payload 만 지원합니다 (Phase 1)")
    }

    private fun inferType(
        value: Any?,
    ): String = when (value) {
        null -> "string"
        is Boolean -> "boolean"
        is Int, is Long, is Short, is Byte, is BigInteger -> "integer"
        is Double, is Float, is BigDecimal -> "number"
        is Number -> "number"
        is Collection<*>, is Array<*> -> "array"
        is Map<*, *> -> "object"
        else -> "string"
    }
}
