package com.debate.pangyeori.support.dsl

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

/**
 * JSON 응답 바디를 필드 단위로 문서화하는 DSL.
 *
 * 단순 필드는 `field()`로 선언하고, 중첩 객체는 `obj { }`, 배열은 `array { }` 블록으로 선언한다.
 * 블록 안에서 선언한 필드 경로에는 부모 경로가 자동으로 prefix로 붙는다.
 *
 * REST Docs는 응답 바디에 존재하는 필드를 빠짐없이 문서화하도록 강제한다.
 * 실제 응답에 있는 필드를 선언하지 않으면 테스트가 실패하므로, 응답 구조가 바뀌면 이 DSL도 함께 수정해야 한다.
 * 문서화하고 싶지 않은 필드가 있다면 `optional()`을 체이닝해 선택 항목으로 처리할 수 있다.
 */
@RestDocsDslMarker
class ResponseBodyDsl(private val prefix: String = "") {
    private val fieldBuilders = mutableListOf<ResponseFieldBuilder>()

    fun field(
        path: String,
        description: String,
        type: JsonFieldType = JsonFieldType.VARIES,
    ): ResponseFieldBuilder =
        ResponseFieldBuilder(prefix + path, description, optional = false, type)
            .also { fieldBuilders += it }

    fun obj(
        path: String,
        description: String,
        block: ResponseBodyDsl.() -> Unit,
    ): ResponseFieldBuilder {
        val builder = ResponseFieldBuilder(prefix + path, description, optional = false, JsonFieldType.OBJECT)
        fieldBuilders += builder
        ResponseBodyDsl("$prefix$path.").apply(block).fieldBuilders.forEach { fieldBuilders += it }
        return builder
    }

    fun array(
        path: String,
        description: String,
        block: ResponseBodyDsl.() -> Unit,
    ): ResponseFieldBuilder {
        val builder = ResponseFieldBuilder(prefix + path, description, optional = false, JsonFieldType.ARRAY)
        fieldBuilders += builder
        ResponseBodyDsl("$prefix$path[].").apply(block).fieldBuilders.forEach { fieldBuilders += it }
        return builder
    }

    internal fun descriptors(): List<FieldDescriptor> = fieldBuilders.map { it.build() }
}

/**
 * 응답 바디 필드 하나를 설정하는 빌더.
 *
 * `optional()`을 호출하면 실제 응답에 해당 필드가 없어도 REST Docs 검증이 통과된다.
 * 반대로 `optional()`이 없는 필드가 응답에 누락되면 테스트가 실패한다.
 * `type()`으로 필드 타입을 명시하면 생성된 스니펫 문서에 타입 정보가 포함된다.
 */
class ResponseFieldBuilder internal constructor(
    private val path: String,
    private val description: String,
    private var optional: Boolean,
    private var type: JsonFieldType,
) {
    fun optional(): ResponseFieldBuilder = apply { optional = true }
    fun type(jsonFieldType: JsonFieldType): ResponseFieldBuilder = apply { type = jsonFieldType }

    internal fun build(): FieldDescriptor =
        fieldWithPath(path).description(description).type(type)
            .let { if (optional) it.optional() else it }
}
