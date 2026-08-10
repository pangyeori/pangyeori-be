package com.debate.pangyeori.support.dsl

import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName

/**
 * 테스트에서 기대하는 응답 상태 코드·바디·헤더를 선언하는 DSL.
 *
 * `status()`를 생략하면 200을 기대한다.
 * `body { }`는 선택 항목으로, 응답 바디 필드를 문서화할 필요가 없으면 생략해도 된다.
 * `body { }`를 선언하면 실제 응답 바디와 선언된 필드 목록이 일치하는지 REST Docs가 자동으로 검증한다.
 * `header()`로 선언한 헤더는 실제 응답에 해당 이름의 헤더가 존재하는지 REST Docs가 검증하며,
 * `Set-Cookie`처럼 쿠키를 응답 헤더로 내려주는 경우를 문서화할 때 사용한다.
 */
@RestDocsDslMarker
class ResponseDsl {
    internal var expectedStatus: Int = 200
    internal var bodyDsl: ResponseBodyDsl? = null
    private val headerDescriptors = mutableListOf<HeaderDescriptor>()

    fun status(code: Int) {
        expectedStatus = code
    }

    fun body(block: ResponseBodyDsl.() -> Unit) {
        bodyDsl = ResponseBodyDsl().apply(block)
    }

    fun header(name: String, description: String) {
        headerDescriptors += headerWithName(name).description(description)
    }

    internal fun headers(): List<HeaderDescriptor> = headerDescriptors
}
