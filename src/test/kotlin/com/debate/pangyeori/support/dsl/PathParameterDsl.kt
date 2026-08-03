package com.debate.pangyeori.support.dsl

import com.epages.restdocs.apispec.ParameterDescriptorWithType
import com.epages.restdocs.apispec.ResourceDocumentation
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName

/**
 * URL 경로 변수를 테스트 값과 문서 설명과 함께 선언하는 DSL.
 *
 * 여기서 지정한 `value`는 문서화 용도뿐 아니라 실제 요청 URL을 조립할 때도 사용된다.
 * 예를 들어 `get("/api/resources/{id}")`에 `param("id", "1", "리소스 ID")`를 선언하면
 * 실제 요청은 `/api/resources/1`로 전송된다.
 *
 * param 선언 순서는 URL 템플릿의 `{변수}` 순서와 맞아야 한다.
 */
@RestDocsDslMarker
class PathParameterDsl {
    internal val params = mutableListOf<PathParamBuilder>()

    fun param(
        name: String,
        value: String,
        description: String,
    ): PathParamBuilder =
        PathParamBuilder(name, value, description, optional = false)
            .also { params += it }

    internal fun pathValues(): Array<Any> = params.map { it.value }.toTypedArray()

    internal fun descriptors(): List<ParameterDescriptor> = params.map { it.build() }

    internal fun resourceDescriptors(): List<ParameterDescriptorWithType> = params.map { it.buildResourceDescriptor() }
}

/**
 * 경로 변수 하나를 설정하는 빌더.
 *
 * `optional()`은 이 경로 변수가 실제 API에서 생략 가능함을 문서에 표시하는 것으로,
 * 테스트 요청 자체에는 항상 값이 포함된다.
 */
class PathParamBuilder internal constructor(
    val name: String,
    val value: String,
    private val description: String,
    private var optional: Boolean,
) {
    fun optional(): PathParamBuilder = apply { optional = true }

    internal fun build(): ParameterDescriptor =
        parameterWithName(name).description(description)
            .also { if (optional) it.optional() }

    internal fun buildResourceDescriptor(): ParameterDescriptorWithType =
        ResourceDocumentation.parameterWithName(name).description(description)
            .also { if (optional) it.optional() }
}
