package com.debate.pangyeori.support.asyncapi.generator

/**
 * 한 번의 문서화가 만들어내는 AsyncAPI 3.0 조각(channels / operations)을 표현한다.
 *
 * [AsyncApiDocumentStore]가 여러 스니펫의 [toFragment] 결과를 deep-merge 해 최종 문서를 만든다.
 */
internal interface AsyncApiSnippet {

    fun toFragment(): Map<String, Any>
}
