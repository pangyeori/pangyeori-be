package com.debate.pangyeori.auth.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.auth.token.RefreshTokenCookieProvider
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class AuthControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    @Test
    fun `signin에 성공하면 access token을 반환하고 refresh token 쿠키를 발급한다`() {
        userRepository.save(
            User.create(
                email = "signin@pangyeori.com",
                password = passwordEncoder.encode("password123!")!!,
                nickname = "로그인사용자",
            ),
        )

        restDocs(mockMvc, "auth/signin") {
            summary("로그인")
            tag("Auth")
            request {
                post("/api/v1/auth/signin")
                body {
                    field("email", "signin@pangyeori.com", "사용자 이메일")
                    field("password", "password123!", "사용자 비밀번호")
                }
            }
            response {
                status(200)
                header("Set-Cookie", "${RefreshTokenCookieProvider.COOKIE_NAME} (발급된 Refresh Token, HttpOnly)")
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "발급된 인증 토큰") {
                        field("accessToken", "API 인증에 사용하는 Access Token")
                        field("tokenType", "인증 헤더 타입")
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
            ),
        )

        restDocs(mockMvc, "auth/signin-invalid-password") {
            summary("로그인")
            tag("Auth")
            request {
                post("/api/v1/auth/signin")
                body {
                    field("email", "wrong-password@pangyeori.com", "사용자 이메일")
                    field("password", "wrong-password", "잘못된 사용자 비밀번호")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `존재하지 않는 이메일이면 동일한 401 응답을 반환한다`() {
        restDocs(mockMvc, "auth/signin-unknown-email") {
            summary("로그인")
            tag("Auth")
            request {
                post("/api/v1/auth/signin")
                body {
                    field("email", "unknown@pangyeori.com", "가입되지 않은 사용자 이메일")
                    field("password", "password123!", "사용자 비밀번호")
                }
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `유효한 refresh token으로 토큰을 재발급한다`() {
        val email = "refresh@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "재발급사용자",
            ),
        )
        val refreshToken = authService.signIn(
            email = email,
            password = password,
        ).refreshToken

        restDocs(mockMvc, "auth/refresh") {
            summary("토큰 재발급")
            tag("Auth")
            request {
                post("/api/v1/auth/refresh")
                cookies {
                    cookie("refreshToken", refreshToken, "재발급에 사용할 Refresh Token")
                }
            }
            response {
                status(200)
                header("Set-Cookie", "${RefreshTokenCookieProvider.COOKIE_NAME} (회전된(rotate) 새 Refresh Token, HttpOnly)")
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "재발급된 인증 토큰") {
                        field("accessToken", "API 인증에 사용하는 Access Token")
                        field("tokenType", "인증 헤더 타입")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `유효한 refresh token으로 로그아웃한다`() {
        val email = "signout@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "로그아웃사용자",
            ),
        )
        val refreshToken = authService.signIn(
            email = email,
            password = password,
        ).refreshToken

        restDocs(mockMvc, "auth/signout") {
            summary("로그아웃")
            tag("Auth")
            request {
                post("/api/v1/auth/signout")
                cookies {
                    cookie("refreshToken", refreshToken, "폐기할 Refresh Token")
                }
            }
            response {
                status(204)
            }
        }
    }

    @Test
    fun `refresh token 쿠키가 없으면 재발급에 실패한다`() {
        restDocs(mockMvc, "auth/refresh-missing-cookie") {
            summary("토큰 재발급")
            tag("Auth")
            request {
                post("/api/v1/auth/refresh")
            }
            response {
                status(401)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `refresh token 쿠키가 없어도 로그아웃에 성공한다`() {
        restDocs(mockMvc, "auth/signout-missing-cookie") {
            summary("로그아웃")
            tag("Auth")
            request {
                post("/api/v1/auth/signout")
            }
            response {
                status(204)
            }
        }
    }
}
