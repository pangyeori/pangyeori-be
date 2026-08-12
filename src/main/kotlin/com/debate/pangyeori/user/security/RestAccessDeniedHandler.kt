package com.debate.pangyeori.user.security

import com.debate.pangyeori.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class RestAccessDeniedHandler(
    private val errorResponseWriter: SecurityErrorResponseWriter,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        errorResponseWriter.write(
            response = response,
            errorCode = ErrorCode.UNAUTHORIZED_ACCESS,
        )
    }
}
