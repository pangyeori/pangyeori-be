package com.debate.pangyeori.config

import com.debate.pangyeori.user.security.JwtAuthenticationFilter
import com.debate.pangyeori.user.security.RestAccessDeniedHandler
import com.debate.pangyeori.user.security.RestAuthenticationEntryPoint
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: RestAuthenticationEntryPoint,
        accessDeniedHandler: RestAccessDeniedHandler,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }
        .exceptionHandling {
            it.authenticationEntryPoint(authenticationEntryPoint)
            it.accessDeniedHandler(accessDeniedHandler)
        }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/api/v1/email-verifications/**",
                "/actuator/health",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/docs/**",
            ).permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
            it.requestMatchers(HttpMethod.GET, "/api/v1/users/nickname/duplicate").permitAll()
            it.requestMatchers(
                HttpMethod.POST,
                "/api/v1/users/signin",
                "/api/v1/users/refresh",
                "/api/v1/users/signout",
            ).permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()

    @Bean
    fun jwtEncoder(
        @Value("\${security.jwt.secret}") secret: String,
    ): JwtEncoder = NimbusJwtEncoder(
        ImmutableSecret<SecurityContext>(secretKey(secret)),
    )

    @Bean
    fun jwtDecoder(
        @Value("\${security.jwt.secret}") secret: String,
        @Value("\${security.jwt.issuer}") issuer: String,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(secretKey(secret))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(issuer),
        )
        return decoder
    }

    private fun secretKey(
        secret: String,
    ): SecretKeySpec {
        require(secret.toByteArray().size >= MINIMUM_SECRET_LENGTH) {
            "security.jwt.secret must be at least $MINIMUM_SECRET_LENGTH bytes"
        }
        return SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM)
    }

    companion object {
        private const val MINIMUM_SECRET_LENGTH = 32
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
