package com.debate.pangyeori.support.dsl

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder

/**
 * HTTP 요청(메서드, URL, 헤더, 바디, 파라미터)을 설정하는 DSL.
 *
 * JSON 바디(`body { }`), 폼 파라미터(`form { }`), 파일 업로드(`multipart { }`)를 지원하며,
 * 셋 중 하나만 사용할 수 있다. 함께 선언하면 multipart → form → body 순으로 하나만 적용된다.
 */
@RestDocsDslMarker
class RequestDsl {
    internal var method: HttpMethod = HttpMethod.GET
        private set
    internal var urlTemplate: String = "/"
        private set
    private val headers: MutableMap<String, String> = mutableMapOf()

    internal var bodyDsl: RequestBodyDsl? = null
    internal var formDsl: FormDsl? = null
    internal var multipartDsl: MultipartDsl? = null
    internal var pathParameterDsl: PathParameterDsl? = null
    internal var queryParameterDsl: QueryParameterDsl? = null

    fun get(urlTemplate: String) = method(HttpMethod.GET, urlTemplate)
    fun post(urlTemplate: String) = method(HttpMethod.POST, urlTemplate)
    fun put(urlTemplate: String) = method(HttpMethod.PUT, urlTemplate)
    fun delete(urlTemplate: String) = method(HttpMethod.DELETE, urlTemplate)
    fun patch(urlTemplate: String) = method(HttpMethod.PATCH, urlTemplate)

    private fun method(httpMethod: HttpMethod, url: String) {
        method = httpMethod
        urlTemplate = url
    }

    fun header(name: String, value: String) {
        headers[name] = value
    }

    fun body(block: RequestBodyDsl.() -> Unit) {
        bodyDsl = RequestBodyDsl().apply(block)
    }

    fun form(block: FormDsl.() -> Unit) {
        formDsl = FormDsl().apply(block)
    }

    fun multipart(block: MultipartDsl.() -> Unit) {
        multipartDsl = MultipartDsl().apply(block)
    }

    fun pathParameters(block: PathParameterDsl.() -> Unit) {
        pathParameterDsl = PathParameterDsl().apply(block)
    }

    fun queryParameters(block: QueryParameterDsl.() -> Unit) {
        queryParameterDsl = QueryParameterDsl().apply(block)
    }

    internal fun buildRequestBuilder(): AbstractMockHttpServletRequestBuilder<*> {
        val pathVars: Array<Any> = pathParameterDsl?.pathValues() ?: emptyArray()

        return when {
            multipartDsl != null -> buildMultipartRequest(pathVars)
            formDsl != null -> buildFormRequest(pathVars)
            else -> buildStandardRequest(pathVars)
        }
    }

    private fun buildStandardRequest(pathVars: Array<Any>): AbstractMockHttpServletRequestBuilder<*> {
        val builder = when (method) {
            HttpMethod.GET -> RestDocumentationRequestBuilders.get(urlTemplate, *pathVars)
            HttpMethod.POST -> RestDocumentationRequestBuilders.post(urlTemplate, *pathVars)
            HttpMethod.PUT -> RestDocumentationRequestBuilders.put(urlTemplate, *pathVars)
            HttpMethod.DELETE -> RestDocumentationRequestBuilders.delete(urlTemplate, *pathVars)
            HttpMethod.PATCH -> RestDocumentationRequestBuilders.patch(urlTemplate, *pathVars)
            else -> error("지원하지 않는 HTTP 메서드: $method")
        }
        applyCommon(builder)
        bodyDsl?.let {
            builder.contentType(MediaType.APPLICATION_JSON)
            builder.content(it.toJson())
        }
        return builder
    }

    private fun buildFormRequest(pathVars: Array<Any>): AbstractMockHttpServletRequestBuilder<*> {
        val builder = when (method) {
            HttpMethod.GET -> RestDocumentationRequestBuilders.get(urlTemplate, *pathVars)
            HttpMethod.POST -> RestDocumentationRequestBuilders.post(urlTemplate, *pathVars)
            HttpMethod.PUT -> RestDocumentationRequestBuilders.put(urlTemplate, *pathVars)
            HttpMethod.PATCH -> RestDocumentationRequestBuilders.patch(urlTemplate, *pathVars)
            else -> error("Form 요청에 지원하지 않는 메서드: $method")
        }
        applyCommon(builder)
        builder.contentType(MediaType.APPLICATION_FORM_URLENCODED)
        formDsl!!.params.forEach { p -> builder.param(p.name, p.value ?: "") }
        return builder
    }

    private fun buildMultipartRequest(pathVars: Array<Any>): MockMultipartHttpServletRequestBuilder {
        val builder = RestDocumentationRequestBuilders.multipart(urlTemplate, *pathVars)
        applyCommon(builder)
        multipartDsl!!.parts.forEach { part ->
            when (part) {
                is MultipartDsl.FilePart -> builder.file(part.toMockMultipartFile())
                is MultipartDsl.TextPart -> builder.param(part.name, part.value)
            }
        }
        return builder
    }

    private fun applyCommon(builder: AbstractMockHttpServletRequestBuilder<*>) {
        headers.forEach { (k, v) -> builder.header(k, v) }
        queryParameterDsl?.params?.forEach { p ->
            p.value?.let { builder.param(p.name, it) }
        }
    }
}
