package com.debate.pangyeori.support.dsl

/**
 * 테스트에서 기대하는 응답 상태 코드와 바디를 선언하는 DSL.
 *
 * `status()`를 생략하면 200을 기대한다.
 * `body { }`는 선택 항목으로, 응답 바디 필드를 문서화할 필요가 없으면 생략해도 된다.
 * `body { }`를 선언하면 실제 응답 바디와 선언된 필드 목록이 일치하는지 REST Docs가 자동으로 검증한다.
 */
@RestDocsDslMarker
class ResponseDsl {
    internal var expectedStatus: Int = 200
    internal var bodyDsl: ResponseBodyDsl? = null

    fun status(code: Int) {
        expectedStatus = code
    }

    fun body(block: ResponseBodyDsl.() -> Unit) {
        bodyDsl = ResponseBodyDsl().apply(block)
    }
}
