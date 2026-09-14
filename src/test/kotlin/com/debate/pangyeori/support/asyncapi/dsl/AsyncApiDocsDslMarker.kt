package com.debate.pangyeori.support.asyncapi.dsl

/**
 * DSL 블록이 바깥 블록의 메서드를 실수로 호출하지 못하도록 스코프를 격리한다.
 *
 * 예를 들어 `receive { }` 안에서 `channel()`을 호출하면 컴파일 에러가 난다.
 */
@DslMarker
annotation class AsyncApiDocsDslMarker
