package com.debate.pangyeori.support.asyncapi.generator

import tools.jackson.databind.ObjectMapper
import java.io.File

/**
 * 스니펫 조각을 `build/asyncapi-snippets/`에 쌓고([writeFragment]),
 * 전부 deep-merge 해 `build/asyncapi/asyncapi.json`으로 조립한다([assemble]).
 *
 * pangyeori-be의 "REST Docs 스니펫 쓰기 + openapi3 태스크 조립" 두 단계에 대응한다.
 * Gradle Test의 workingDir이 프로젝트 루트라 상대 경로 상수로 충분하다.
 */
internal class AsyncApiDocumentStore(
    private val objectMapper: ObjectMapper,
) {

    fun writeFragment(
        name: String,
        fragment: Map<String, Any>,
    ) {
        val directory = File(SNIPPETS_DIR)
        directory.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(File(directory, "$name.json"), fragment)
    }

    fun assemble() {
        val fragmentFiles = File(SNIPPETS_DIR)
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()

        if (fragmentFiles.isEmpty()) {
            throw IllegalStateException("조립할 스니펫 조각이 없습니다: $SNIPPETS_DIR")
        }

        val document = baseDocument()
        fragmentFiles.forEach { file ->
            deepMerge(
                target = document,
                source = readFragment(
                    file = file,
                ),
            )
        }

        val output = File(OUTPUT_PATH)
        output.parentFile?.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output, document)
    }

    private fun baseDocument(): MutableMap<String, Any> = linkedMapOf(
        "asyncapi" to ASYNCAPI_VERSION,
        "info" to linkedMapOf<String, Any>(
            "title" to DOC_TITLE,
            "version" to DOC_VERSION,
        ),
        "servers" to linkedMapOf<String, Any>(
            SERVER_NAME to linkedMapOf<String, Any>(
                "host" to SERVER_HOST,
                "protocol" to SERVER_PROTOCOL,
            ),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    private fun readFragment(
        file: File,
    ): Map<String, Any> = objectMapper.readValue(file, Map::class.java) as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun deepMerge(
        target: MutableMap<String, Any>,
        source: Map<String, Any>,
    ) {
        source.forEach { (key, value) ->
            val existing = target[key]
            when {
                existing is MutableMap<*, *> && value is Map<*, *> ->
                    deepMerge(existing as MutableMap<String, Any>, value as Map<String, Any>)

                // 같은 메시지를 여러 문서 테스트가 다루면 예시를 덮어쓰지 않고 나란히 쌓는다.
                key == "examples" && existing is MutableList<*> && value is List<*> -> {
                    val merged = existing as MutableList<Any>
                    merged.addAll((value as List<Any>).filterNot { it in merged })
                }

                else -> target[key] = value
            }
        }
    }

    companion object {
        private const val SERVER_NAME = "production"

        internal const val SERVER_REF = "#/servers/$SERVER_NAME"

        private const val SNIPPETS_DIR = "build/asyncapi-snippets"
        private const val OUTPUT_PATH = "build/asyncapi/asyncapi.json"
        private const val ASYNCAPI_VERSION = "3.0.0"
        private const val DOC_TITLE = "Pangyeori AsyncAPI"
        private const val DOC_VERSION = "0.0.1"
        private const val SERVER_HOST = "localhost:8080"
        private const val SERVER_PROTOCOL = "https"
    }
}
