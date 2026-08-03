package com.debate.pangyeori.support.dsl

import com.epages.restdocs.apispec.ResourceDocumentation
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * DSL 블록이 바깥 블록의 메서드를 실수로 호출하지 못하도록 스코프를 격리하는 어노테이션.
 *
 * 예를 들어 `response { }` 블록 안에서 `request { }`의 메서드를 호출하는 것처럼,
 * 중첩된 블록 간 의도치 않은 참조를 컴파일 타임에 방지한다.
 */
@DslMarker
annotation class RestDocsDslMarker

/**
 * REST Docs 문서화 DSL의 시작점.
 *
 * MockMvc 요청을 실행하고, 선언된 파라미터·필드를 기반으로
 * `build/generated-snippets/{identifier}/` 아래에 REST Docs 스니펫 파일을 생성한다.
 *
 * @param identifier 스니펫이 저장될 폴더 이름. 리소스와 동작을 `/`로 구분해 지정한다 (resource/action)
 */
fun restDocs(
    mockMvc: MockMvc,
    identifier: String,
    block: RestDocsDsl.() -> Unit,
): ResultActions = RestDocsDsl(mockMvc, identifier).apply(block).execute()

/**
 * `restDocs { }` 블록 내부에서 사용하는 DSL 객체.
 *
 * `request { }`는 필수이며, `response { }`는 생략하면 상태 코드 200을 기대한다.
 * `summary()`를 선언하면 OpenAPI 스니펫(`openapi-resource.json`)이 함께 생성되어
 * `./gradlew generateDocs` 실행 시 Swagger UI용 YAML에 반영된다.
 */
@RestDocsDslMarker
class RestDocsDsl(
    private val mockMvc: MockMvc,
    private val identifier: String,
) {
    private var summary: String? = null
    private var tag: String? = null
    private var requestDsl: RequestDsl? = null
    private var responseDsl: ResponseDsl = ResponseDsl()

    fun summary(value: String) {
        summary = value
    }

    fun tag(value: String) {
        tag = value
    }

    fun request(block: RequestDsl.() -> Unit) {
        requestDsl = RequestDsl().apply(block)
    }

    fun response(block: ResponseDsl.() -> Unit) {
        responseDsl = ResponseDsl().apply(block)
    }

    internal fun execute(): ResultActions {
        val req = requireNotNull(requestDsl) { "request { } 블록이 필요합니다" }
        val res = responseDsl

        val snippets = buildSnippets(req, res)
        val requestBuilder = req.buildRequestBuilder()

        return mockMvc.perform(requestBuilder)
            .andExpect(status().`is`(res.expectedStatus))
            .andDo(MockMvcRestDocumentation.document(identifier, *snippets.toTypedArray()))
    }

    private fun buildSnippets(req: RequestDsl, res: ResponseDsl): List<Snippet> {
        val snippets = mutableListOf<Snippet>()

        req.pathParameterDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += RequestDocumentation.pathParameters(*it.toTypedArray()) }

        req.queryParameterDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += RequestDocumentation.queryParameters(*it.toTypedArray()) }

        req.formDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += RequestDocumentation.formParameters(*it.toTypedArray()) }

        req.bodyDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += PayloadDocumentation.requestFields(*it.toTypedArray()) }

        res.bodyDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += PayloadDocumentation.responseFields(*it.toTypedArray()) }

        summary?.let { sum ->
            val resolvedTag = tag ?: identifier.substringBefore("/")
                .replaceFirstChar { it.uppercase() }

            val builder = ResourceSnippetParameters.builder()
                .tag(resolvedTag)
                .summary(sum)

            req.pathParameterDsl?.resourceDescriptors()
                ?.takeIf { it.isNotEmpty() }
                ?.let { builder.pathParameters(*it.toTypedArray()) }

            req.queryParameterDsl?.resourceDescriptors()
                ?.takeIf { it.isNotEmpty() }
                ?.let { builder.queryParameters(*it.toTypedArray()) }

            req.bodyDsl?.descriptors()
                ?.takeIf { it.isNotEmpty() }
                ?.let { builder.requestFields(*it.toTypedArray()) }

            res.bodyDsl?.descriptors()
                ?.takeIf { it.isNotEmpty() }
                ?.let { builder.responseFields(*it.toTypedArray()) }

            snippets += ResourceDocumentation.resource(builder.build())
        }

        return snippets
    }
}
