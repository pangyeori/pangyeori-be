package com.debate.pangyeori.user.security

import com.debate.pangyeori.common.dto.ApiError
import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class SecurityErrorResponseWriter(
    private val objectMapper: ObjectMapper,
) {

    fun write(
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ApiResponse.fail(
                error = ApiError(
                    code = errorCode.name,
                    message = errorCode.message,
                ),
            ),
        )
    }
}
