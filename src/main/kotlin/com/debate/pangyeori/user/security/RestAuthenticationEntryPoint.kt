package com.debate.pangyeori.user.security

import com.debate.pangyeori.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class RestAuthenticationEntryPoint(
    private val errorWriter: SecurityErrorResponseWriter,
) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) {
        errorWriter.write(response, ErrorCode.INVALID_TOKEN)
    }
}
