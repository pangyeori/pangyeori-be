package com.debate.pangyeori.user.controller

import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.user.repository.UserRepository
import com.debate.pangyeori.user.service.UserAuthService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class UserControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerificationRedisRepository: EmailVerificationRedisRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var userAuthService: UserAuthService

    @Test
    fun `로그인한 사용자가 내 정보를 조회한다`() {
        val email = "mypage@pangyeori.com"
        val password = "password123!"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode(password)!!,
                nickname = "마이페이지사용자",
                profileImageUrl = "https://example.com/profile.png",
            ),
        )
        val accessToken = userAuthService.signIn(
            email = email,
            password = password,
        ).accessToken

        restDocs(mockMvc, "users/get-my-info") {
            summary("내 정보 조회")
            tag("Users")
            request {
                get("/api/v1/users/me")
                header("Authorization", "Bearer $accessToken")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "로그인한 사용자 정보") {
                        field("id", "사용자 ID")
                        field("email", "사용자 이메일")
                        field("nickname", "사용자 닉네임")
                        field("profileImageUrl", "프로필 이미지 URL").optional()
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `인증 정보 없이 내 정보를 조회하면 401을 반환한다`() {
        restDocs(mockMvc, "users/get-my-info-unauthorized") {
            summary("내 정보 조회")
            tag("Users")
            request {
                get("/api/v1/users/me")
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
    fun `회원가입에 성공한다`() {
        val email = "signup-success@pangyeori.com"
        emailVerificationRedisRepository.markVerified(
            email = email,
        )

        restDocs(mockMvc, "users/create") {
            summary("회원가입")
            request {
                post("/api/v1/users")
                body {
                    field("email", email, "인증을 완료한 이메일")
                    field("password", "password123!", "8자 이상 64자 이하 비밀번호")
                    field("nickname", "판결이", "사용할 닉네임")
                    field("profileImageUrl", "https://example.com/profile.png", "프로필 이미지 URL").optional()
                }
            }
            response {
                status(201)
            }
        }
    }

    @Test
    fun `닉네임 중복 여부를 확인한다`() {
        userRepository.save(
            User.create(
                email = "nickname-check@pangyeori.com",
                password = "encoded-password",
                nickname = "판결이",
                profileImageUrl = null,
            ),
        )

        restDocs(mockMvc, "users/check-nickname-duplicate") {
            summary("닉네임 중복 확인")
            request {
                get("/api/v1/users/nickname/duplicate")
                queryParameters {
                    param("nickname", "판결이", "중복 여부를 확인할 닉네임")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "닉네임 중복 확인 결과") {
                        field("duplicated", "닉네임 중복 여부")
                    }
                    field("error", "에러 정보").optional()
                }
            }
        }
    }

    @Test
    fun `닉네임을 입력하지 않으면 400을 반환한다`() {
        restDocs(mockMvc, "users/check-nickname-duplicate-missing-nickname") {
            summary("닉네임 중복 확인")
            request {
                get("/api/v1/users/nickname/duplicate")
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `닉네임이 2자 미만이면 400을 반환한다`() {
        restDocs(mockMvc, "users/check-nickname-duplicate-too-short") {
            summary("닉네임 중복 확인")
            request {
                get("/api/v1/users/nickname/duplicate")
                queryParameters {
                    param("nickname", "a", "2자 미만의 닉네임")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `닉네임이 50자를 초과하면 400을 반환한다`() {
        restDocs(mockMvc, "users/check-nickname-duplicate-too-long") {
            summary("닉네임 중복 확인")
            request {
                get("/api/v1/users/nickname/duplicate")
                queryParameters {
                    param("nickname", "a".repeat(51), "50자를 초과하는 닉네임")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `이메일 인증을 완료하지 않으면 422를 반환한다`() {
        restDocs(mockMvc, "users/create-not-verified") {
            summary("회원가입")
            request {
                post("/api/v1/users")
                body {
                    field("email", "not-verified@pangyeori.com", "인증하지 않은 이메일")
                    field("password", "password123!", "비밀번호")
                    field("nickname", "미인증사용자", "닉네임")
                }
            }
            response {
                status(422)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        field("details", "필드별 검증 오류").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `가입된 이메일이면 409를 반환한다`() {
        val email = "duplicate@pangyeori.com"
        userRepository.save(
            User.create(
                email = email,
                password = "encoded-password",
                nickname = "기존사용자",
                profileImageUrl = null,
            ),
        )
        emailVerificationRedisRepository.markVerified(
            email = email,
        )

        restDocs(mockMvc, "users/create-duplicate-email") {
            summary("회원가입")
            request {
                post("/api/v1/users")
                body {
                    field("email", email, "이미 가입된 이메일")
                    field("password", "password123!", "비밀번호")
                    field("nickname", "새사용자", "닉네임")
                }
            }
            response {
                status(409)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        field("details", "필드별 검증 오류").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `회원가입 필수값이 누락되면 400을 반환한다`() {
        restDocs(mockMvc, "users/create-invalid-input") {
            summary("회원가입")
            request {
                post("/api/v1/users")
                body {
                    rawJson("{}")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "에러 정보") {
                        field("code", "에러 코드")
                        field("message", "에러 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }
}
