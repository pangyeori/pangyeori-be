package com.debate.pangyeori.support.dsl

import com.epages.restdocs.apispec.ResourceDocumentation
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.springframework.http.HttpHeaders
import org.springframework.restdocs.cookies.CookieDocumentation
import org.springframework.restdocs.headers.HeaderDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.operation.OperationRequest
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.OperationResponseFactory
import org.springframework.restdocs.operation.preprocess.OperationPreprocessor
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.regex.Pattern

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

    // JWT는 header·payload가 항상 "eyJ"(= '{"'의 base64url 인코딩 결과)로 시작하므로, 이 특징으로 좁혀 매치해 응답 바디·헤더에 실제 토큰 값이 노출되지 않도록 마스킹
    private val jwtPattern: Pattern = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    private val maskedJwt = "<jwtToken>"

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
        val requestMaskPreprocessors = req.bodyDsl?.maskPreprocessors().orEmpty()
        val responseMaskPreprocessors = res.bodyDsl?.maskPreprocessors().orEmpty()

        val snippets = buildSnippets(req, res)
        val requestBuilder = req.buildRequestBuilder()

        return mockMvc.perform(requestBuilder)
            .andExpect(status().`is`(res.expectedStatus))
            .andDo(
                MockMvcRestDocumentation.document(
                    identifier,
                    preprocessRequest(*requestMaskPreprocessors.toTypedArray()),
                    preprocessResponse(
                        replacePattern(jwtPattern, maskedJwt),
                        jwtHeaderMaskingPreprocessor(),
                        *responseMaskPreprocessors.toTypedArray(),
                    ),
                    *snippets.toTypedArray(),
                ),
            )
    }

    // Set-Cookie 값 마스킹을 위해 OperationPreprocessor
    private fun jwtHeaderMaskingPreprocessor(): OperationPreprocessor = object : OperationPreprocessor {
        private val responseFactory = OperationResponseFactory()

        override fun preprocess(request: OperationRequest) = request

        override fun preprocess(response: OperationResponse): OperationResponse {
            val maskedHeaders = HttpHeaders()
            response.headers.forEach { name, values ->
                maskedHeaders.put(name, values.map { jwtPattern.matcher(it).replaceAll(maskedJwt) })
            }

            return responseFactory.createFrom(response, maskedHeaders)
        }
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

        req.cookieDsl?.descriptors()
            ?.takeIf { it.isNotEmpty() }
            ?.let { snippets += CookieDocumentation.requestCookies(*it.toTypedArray()) }

        res.headers()
            .takeIf { it.isNotEmpty() }
            ?.let { snippets += HeaderDocumentation.responseHeaders(*it.toTypedArray()) }

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

            // com.epages:restdocs-api-spec 0.20.1은 OpenAPI 쿠키 파라미터를 지원하지 않아
            // 요청 쿠키는 Swagger 문서에 반영할 수 없다. 응답 쿠키는 Set-Cookie 헤더로 대신 문서화한다.
            res.headers()
                .takeIf { it.isNotEmpty() }
                ?.let { builder.responseHeaders(*it.toTypedArray()) }

            snippets += ResourceDocumentation.resource(builder.build())
        }

        return snippets
    }
}
