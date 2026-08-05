package com.debate.pangyeori.user.controller

import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerificationRedisRepository: EmailVerificationRedisRepository

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
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "생성된 회원 정보") {
                        field("id", "회원 ID")
                        field("email", "회원 이메일")
                        field("nickname", "회원 닉네임")
                        field("profileImageUrl", "프로필 이미지 URL").optional()
                        field("role", "회원 역할")
                        field("status", "회원 상태")
                    }
                    field("error", "에러 정보").optional()
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
