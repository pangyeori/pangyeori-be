package com.debate.pangyeori.support.dsl

import com.epages.restdocs.apispec.ParameterDescriptorWithType
import com.epages.restdocs.apispec.ResourceDocumentation
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName

/**
 * 쿼리 파라미터를 테스트 값과 문서 설명과 함께 선언하는 DSL.
 *
 * `value`를 `null`로 넘기면 실제 요청에는 해당 파라미터가 포함되지 않는다.
 * 예를 들어 페이지 번호처럼 생략 시 기본값이 적용되는 파라미터를 테스트할 때 유용하다.
 * 이 경우에도 `optional()`을 함께 선언해 문서에는 선택 항목으로 표시하는 것이 좋다.
 */
@RestDocsDslMarker
class QueryParameterDsl {
    internal val params = mutableListOf<QueryParamBuilder>()

    fun param(
        name: String,
        value: String?,
        description: String,
    ): QueryParamBuilder =
        QueryParamBuilder(name, value, description, optional = false)
            .also { params += it }

    internal fun descriptors(): List<ParameterDescriptor> = params.map { it.build() }

    internal fun resourceDescriptors(): List<ParameterDescriptorWithType> = params.map { it.buildResourceDescriptor() }
}

/**
 * 쿼리 파라미터 하나를 설정하는 빌더.
 *
 * `optional()`을 호출하면 REST Docs 문서에 선택 항목으로 표시되며,
 * 실제 요청에서 해당 파라미터가 없어도 테스트가 실패하지 않는다.
 */
class QueryParamBuilder internal constructor(
    val name: String,
    val value: String?,
    private val description: String,
    private var optional: Boolean,
) {
    fun optional(): QueryParamBuilder = apply { optional = true }

    internal fun build(): ParameterDescriptor =
        parameterWithName(name).description(description)
            .also { if (optional) it.optional() }

    internal fun buildResourceDescriptor(): ParameterDescriptorWithType =
        ResourceDocumentation.parameterWithName(name).description(description)
            .also { if (optional) it.optional() }
}
