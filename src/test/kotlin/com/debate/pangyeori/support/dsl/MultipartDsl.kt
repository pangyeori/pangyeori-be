package com.debate.pangyeori.support.dsl

import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

/**
 * 파일 업로드(`multipart/form-data`) 요청의 파트를 선언하는 DSL.
 *
 * `file()`로 파일 파트를, `text()`로 일반 텍스트 파트를 추가하며,
 * 두 종류를 섞어서 여러 파트를 선언할 수 있다.
 * 선언한 순서대로 요청에 포함되며, `content`를 생략하면 빈 바이트 배열이 전송된다.
 */
@RestDocsDslMarker
class MultipartDsl {
    internal val parts = mutableListOf<Part>()

    fun file(
        paramName: String,
        originalFilename: String,
        contentType: String = MediaType.APPLICATION_OCTET_STREAM_VALUE,
        content: ByteArray = ByteArray(0),
    ) {
        parts += FilePart(paramName, originalFilename, contentType, content)
    }

    fun text(name: String, value: String) {
        parts += TextPart(name, value)
    }

    sealed interface Part

    internal data class FilePart(
        val paramName: String,
        val originalFilename: String,
        val contentType: String,
        val content: ByteArray,
    ) : Part {
        fun toMockMultipartFile(): MockMultipartFile =
            MockMultipartFile(paramName, originalFilename, contentType, content)
    }

    internal data class TextPart(val name: String, val value: String) : Part
}
