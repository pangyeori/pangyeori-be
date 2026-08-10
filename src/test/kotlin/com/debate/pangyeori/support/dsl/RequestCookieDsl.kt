package com.debate.pangyeori.support.dsl

import org.springframework.restdocs.cookies.CookieDescriptor
import org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName

/**
 * 요청에 실어 보낼 쿠키를 테스트 값과 문서 설명과 함께 선언하는 DSL.
 *
 * `cookie()`로 등록한 값은 실제 요청에 `Cookie` 헤더로 실리는 동시에,
 * REST Docs의 request-cookies 스니펫 문서화에도 사용된다.
 */
@RestDocsDslMarker
class RequestCookieDsl {
    internal val cookies = mutableListOf<RequestCookieBuilder>()

    fun cookie(
        name: String,
        value: String,
        description: String,
    ): RequestCookieBuilder =
        RequestCookieBuilder(name, value, description, optional = false)
            .also { cookies += it }

    internal fun descriptors(): List<CookieDescriptor> = cookies.map { it.build() }
}

/**
 * 요청 쿠키 하나를 설정하는 빌더.
 *
 * `optional()`을 호출하면 REST Docs 문서에 선택 항목으로 표시된다.
 */
class RequestCookieBuilder internal constructor(
    val name: String,
    val value: String,
    private val description: String,
    private var optional: Boolean,
) {
    fun optional(): RequestCookieBuilder = apply { optional = true }

    internal fun build(): CookieDescriptor =
        cookieWithName(name).description(description)
            .let { if (optional) it.optional() else it }
}
