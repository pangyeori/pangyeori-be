package com.debate.pangyeori.config

import com.debate.pangyeori.auth.security.JwtAuthenticationFilter
import com.debate.pangyeori.auth.security.RestAccessDeniedHandler
import com.debate.pangyeori.auth.security.RestAuthenticationEntryPoint
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
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: RestAuthenticationEntryPoint,
        accessDeniedHandler: RestAccessDeniedHandler,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource) }
        .sessionManagement {
            it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }
        .exceptionHandling {
            it.authenticationEntryPoint(authenticationEntryPoint)
            it.accessDeniedHandler(accessDeniedHandler)
        }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.GET, *PUBLIC_GET_PATHS).permitAll()
            it.requestMatchers(HttpMethod.POST, *PUBLIC_POST_PATHS).permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()

    @Bean
    fun corsConfigurationSource(
        @Value("\${cors.allowed-origins}") allowedOrigins: List<String>,
    ): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = ALLOWED_METHODS
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration(CORS_PATH_PATTERN, configuration)
        return source
    }

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
        if (secret.toByteArray().size < MINIMUM_SECRET_LENGTH) {
            throw IllegalArgumentException("security.jwt.secret must be at least $MINIMUM_SECRET_LENGTH bytes")
        }
        return SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM)
    }

    companion object {
        private const val MINIMUM_SECRET_LENGTH = 32
        private const val HMAC_ALGORITHM = "HmacSHA256"

        private val PUBLIC_GET_PATHS = arrayOf(
            "/actuator/health",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/docs/**",
            "/api/v1/users/nickname/duplicate",
        )

        private val PUBLIC_POST_PATHS = arrayOf(
            "/api/v1/email-verifications/**",
            "/api/v1/password-resets/**",
            "/api/v1/users",
            "/api/v1/auth/signin",
            "/api/v1/auth/refresh",
            "/api/v1/auth/signout",
        )

        private const val CORS_PATH_PATTERN = "/api/**"
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    }
}
