package com.debate.pangyeori.support.dsl

import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName

/**
 * 폼 파라미터(`application/x-www-form-urlencoded`)를 테스트 값과 문서 설명과 함께 선언하는 DSL.
 *
 * `form { }` 블록을 사용하면 요청의 `Content-Type`이 자동으로 `application/x-www-form-urlencoded`로 설정된다.
 * 파일을 포함한 멀티파트 요청은 `multipart { }`를 사용해야 하며, 두 블록을 함께 선언하면 `multipart`가 우선 적용된다.
 */
@RestDocsDslMarker
class FormDsl {
    internal val params = mutableListOf<FormParamBuilder>()

    fun field(
        name: String,
        value: String?,
        description: String,
    ): FormParamBuilder =
        FormParamBuilder(name, value, description, optional = false)
            .also { params += it }

    internal fun descriptors(): List<ParameterDescriptor> = params.map { it.build() }
}

/**
 * 폼 파라미터 하나를 설정하는 빌더.
 *
 * `value`가 `null`이면 빈 문자열(`""`)로 전송된다.
 * `optional()`을 호출하면 해당 필드가 요청에 없어도 REST Docs 검증을 통과한다.
 */
class FormParamBuilder internal constructor(
    val name: String,
    val value: String?,
    private val description: String,
    private var optional: Boolean,
) {
    fun optional(): FormParamBuilder = apply { optional = true }

    internal fun build(): ParameterDescriptor =
        parameterWithName(name).description(description)
            .also { if (optional) it.optional() }
}
