package com.debate.pangyeori.user.security

import com.debate.pangyeori.common.exception.ErrorCode
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.exception.ExpiredTokenException
import com.debate.pangyeori.user.exception.InvalidTokenException
import com.debate.pangyeori.user.repository.UserRepository
import com.debate.pangyeori.user.token.TokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val tokenProvider: TokenProvider,
    private val userRepository: UserRepository,
    private val errorResponseWriter: SecurityErrorResponseWriter,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.getHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()

        if (token.isNullOrEmpty()) {
            filterChain.doFilter(request, response)
            return
        }

        val claims = try {
            tokenProvider.parseAccessToken(token)
        } catch (e: ExpiredTokenException) {
            SecurityContextHolder.clearContext()
            errorResponseWriter.write(
                response = response,
                errorCode = ErrorCode.EXPIRED_TOKEN,
            )
            return
        } catch (e: InvalidTokenException) {
            SecurityContextHolder.clearContext()
            errorResponseWriter.write(
                response = response,
                errorCode = ErrorCode.INVALID_TOKEN,
            )
            return
        }

        val user = userRepository.findByEmail(
            email = claims.subject,
        )?.takeIf { it.status == UserStatus.ACTIVE }
        if (user == null) {
            errorResponseWriter.write(
                response = response,
                errorCode = ErrorCode.INVALID_TOKEN,
            )
            return
        }

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            user.email,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
        )
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
