package com.debate.pangyeori.support.asyncapi.dsl

/**
 * `channel(path, protocol, description)`로 선언하는 채널 메타데이터.
 *
 * [name]을 지정하지 않으면 경로에서 채널명을 자동으로 유도한다(camelCase 조합).
 */
internal data class ChannelSpec(
    val path: String,
    val protocol: String,
    val description: String,
    val name: String? = null,
)

/**
 * `parameter(name, description)`로 선언한 채널 주소 파라미터 하나.
 *
 * 채널 주소에 `{debateId}` 같은 치환자가 있으면 AsyncAPI `parameters`로 문서화한다.
 */
internal data class ParameterSpec(
    val name: String,
    val description: String,
)

/**
 * `field(path, description)`로 선언한 payload 필드 하나.
 *
 * [optional]이면 payload에 해당 필드가 없어도 검증을 통과한다. 타입은 캡처된 실제 값에서 추론한다.
 */
@AsyncApiDocsDslMarker
class FieldSpec internal constructor(
    internal val path: String,
    internal val description: String,
) {

    internal var optional: Boolean = false

    fun optional() = apply { optional = true }
}
