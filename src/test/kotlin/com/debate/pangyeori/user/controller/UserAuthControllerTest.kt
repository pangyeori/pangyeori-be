package com.debate.pangyeori.user.controller

import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import tools.jackson.databind.ObjectMapper

class UserAuthControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `로그인에 성공하면 access token과 refresh token을 반환한다`() {
        userRepository.save(
            User.create(
                email = "signin@pangyeori.com",
                password = passwordEncoder.encode("password123!")!!,
                nickname = "로그인사용자",
                profileImageUrl = null,
            ),
        )

        restDocs(mockMvc, "users/signin") {
            summary("로그인")
            tag("Users")
            request {
                post("/api/v1/users/signin")
                body {
                    field("email", "SIGNIN@pangyeori.com", "사용자 이메일")
                    field("password", "password123!", "사용자 비밀번호")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "발급된 인증 토큰") {
                        field("accessToken", "API 인증에 사용하는 Access Token")
                        field("refreshToken", "토큰 재발급에 사용하는 Refresh Token")
                        field("tokenType", "인증 헤더 타입")
                        field("accessTokenExpiresIn", "Access Token 만료 시간(초)")
                        field("refreshTokenExpiresIn", "Refresh Token 만료 시간(초)")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `비밀번호가 일치하지 않으면 401을 반환한다`() {
        userRepository.save(
            User.create(
                email = "wrong-password@pangyeori.com",
                password = passwordEncoder.encode("password123!")!!,
                nickname = "비밀번호오류",
                profileImageUrl = null,
            ),
        )

        mockMvc.post("/api/v1/users/signin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"wrong-password@pangyeori.com","password":"wrong-password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `존재하지 않는 이메일이면 동일한 401 응답을 반환한다`() {
        mockMvc.post("/api/v1/users/signin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"unknown@pangyeori.com","password":"password123!"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `로그인 입력값이 올바르지 않으면 400을 반환한다`() {
        mockMvc.post("/api/v1/users/signin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"invalid-email","password":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details") { isArray() }
        }
    }

    @Test
    fun `refresh token을 회전하고 로그아웃하면 재사용할 수 없다`() {
        userRepository.save(
            User.create(
                email = "token-flow@pangyeori.com",
                password = passwordEncoder.encode("password123!")!!,
                nickname = "토큰흐름",
                profileImageUrl = null,
            ),
        )

        val signInBody = mockMvc.post("/api/v1/users/signin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"token-flow@pangyeori.com","password":"password123!"}"""
        }.andReturn().response.contentAsString
        val oldRefreshToken = objectMapper.readTree(signInBody).path("data").path("refreshToken").stringValue()

        val refreshBody = mockMvc.post("/api/v1/users/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to oldRefreshToken))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { isNotEmpty() }
            jsonPath("$.data.refreshToken") { isNotEmpty() }
        }.andReturn().response.contentAsString
        val newRefreshToken = objectMapper.readTree(refreshBody).path("data").path("refreshToken").stringValue()

        mockMvc.post("/api/v1/users/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to oldRefreshToken))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("INVALID_TOKEN") }
        }

        mockMvc.post("/api/v1/users/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to newRefreshToken))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.post("/api/v1/users/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to newRefreshToken))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("INVALID_TOKEN") }
        }
    }

    @Test
    fun `보호된 경로는 유효한 access token이 필요하다`() {
        userRepository.save(
            User.create(
                email = "protected@pangyeori.com",
                password = passwordEncoder.encode("password123!")!!,
                nickname = "보호경로",
                profileImageUrl = null,
            ),
        )

        mockMvc.get("/api/v1/users/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("INVALID_TOKEN") }
        }

        val signInBody = mockMvc.post("/api/v1/users/signin") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"protected@pangyeori.com","password":"password123!"}"""
        }.andReturn().response.contentAsString
        val accessToken = objectMapper.readTree(signInBody).path("data").path("accessToken").stringValue()

        mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value("protected@pangyeori.com") }
        }
    }
}
