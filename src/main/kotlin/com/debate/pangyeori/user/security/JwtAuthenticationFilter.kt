package com.debate.pangyeori.user.security

import com.debate.pangyeori.user.exception.ExpiredTokenException
import com.debate.pangyeori.user.exception.InvalidTokenException
import com.debate.pangyeori.user.token.TokenProvider
import com.debate.pangyeori.common.exception.ErrorCode
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.repository.UserRepository
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
    private val errorWriter: SecurityErrorResponseWriter,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()

        if (token.isNullOrEmpty()) {
            chain.doFilter(request, response)
            return
        }

        val claims = try {
            tokenProvider.parseAccessToken(token)
        } catch (e: ExpiredTokenException) {
            SecurityContextHolder.clearContext()
            errorWriter.write(response, ErrorCode.EXPIRED_TOKEN)
            return
        } catch (e: InvalidTokenException) {
            SecurityContextHolder.clearContext()
            errorWriter.write(response, ErrorCode.INVALID_TOKEN)
            return
        }

        val user = userRepository.findByEmail(claims.subject)
            ?.takeIf { it.status == UserStatus.ACTIVE }
        if (user == null) {
            errorWriter.write(response, ErrorCode.INVALID_TOKEN)
            return
        }
        val authentication = UsernamePasswordAuthenticationToken(
            user.email,
            null,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
        )
        SecurityContextHolder.getContext().authentication = authentication
        chain.doFilter(request, response)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
