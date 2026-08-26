package com.debate.pangyeori.auth.controller

import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.auth.service.PasswordResetService
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordResetControllerTest : RestDocsMvcTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var emailVerificationRedisRepository: EmailVerificationRedisRepository

    @Autowired
    private lateinit var passwordResetService: PasswordResetService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `재설정 토큰 발급에 성공한다`() {
        val email = "issue-success@pangyeori.com"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode("password123!")!!,
                nickname = "재설정사용자1",
                profileImageUrl = null,
            ),
        )
        emailVerificationRedisRepository.markVerified(
            email = email,
        )

        restDocs(mockMvc, "password-reset/issue") {
            summary("비밀번호 재설정 토큰 발급")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets")
                body {
                    field("email", email, "인증을 완료한 이메일")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    obj("data", "발급된 재설정 토큰") {
                        field("passwordResetToken", "비밀번호 재설정에 사용할 토큰").mask("<passwordResetToken>")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `이메일 인증을 완료하지 않으면 422를 반환한다`() {
        restDocs(mockMvc, "password-reset/issue-not-verified") {
            summary("비밀번호 재설정 토큰 발급")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets")
                body {
                    field("email", "not-verified@pangyeori.com", "인증하지 않은 이메일")
                }
            }
            response {
                status(422)
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
    fun `가입되지 않은 이메일이면 404를 반환한다`() {
        val email = "issue-unknown@pangyeori.com"
        emailVerificationRedisRepository.markVerified(
            email = email,
        )

        restDocs(mockMvc, "password-reset/issue-user-not-found") {
            summary("비밀번호 재설정 토큰 발급")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets")
                body {
                    field("email", email, "가입되지 않은 이메일")
                }
            }
            response {
                status(404)
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
    fun `이메일 형식이 올바르지 않으면 400을 반환한다`() {
        restDocs(mockMvc, "password-reset/issue-invalid-email") {
            summary("비밀번호 재설정 토큰 발급")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets")
                body {
                    field("email", "invalid-email", "형식이 잘못된 이메일")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `발급된 토큰으로 비밀번호 재설정에 성공한다`() {
        val email = "reset-success@pangyeori.com"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode("password123!")!!,
                nickname = "재설정사용자2",
                profileImageUrl = null,
            ),
        )
        emailVerificationRedisRepository.markVerified(
            email = email,
        )
        val passwordResetToken = passwordResetService.issueToken(
            email = email,
        ).passwordResetToken

        restDocs(mockMvc, "password-reset/confirm") {
            summary("비밀번호 재설정")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets/confirm")
                body {
                    field("passwordResetToken", passwordResetToken, "발급된 재설정 토큰").mask("<passwordResetToken>")
                    field("newPassword", "newPassword123!", "새로 설정할 비밀번호")
                }
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    field("error", "오류 정보").optional()
                }
            }
        }
    }

    @Test
    fun `유효하지 않은 토큰으로 재설정을 요청하면 422를 반환한다`() {
        restDocs(mockMvc, "password-reset/confirm-token-not-found") {
            summary("비밀번호 재설정")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets/confirm")
                body {
                    field("passwordResetToken", "invalid-or-expired-token", "존재하지 않거나 만료된 토큰")
                    field("newPassword", "newPassword123!", "새로 설정할 비밀번호")
                }
            }
            response {
                status(422)
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
    fun `이미 사용된 토큰으로 재설정을 요청하면 422를 반환한다`() {
        val email = "reset-already-used@pangyeori.com"
        userRepository.save(
            User.create(
                email = email,
                password = passwordEncoder.encode("password123!")!!,
                nickname = "재설정사용자3",
                profileImageUrl = null,
            ),
        )
        emailVerificationRedisRepository.markVerified(
            email = email,
        )
        val passwordResetToken = passwordResetService.issueToken(
            email = email,
        ).passwordResetToken
        passwordResetService.resetPassword(
            passwordResetToken = passwordResetToken,
            newPassword = "newPassword123!",
        )

        restDocs(mockMvc, "password-reset/confirm-already-used") {
            summary("비밀번호 재설정")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets/confirm")
                body {
                    field("passwordResetToken", passwordResetToken, "이미 사용된 토큰").mask("<passwordResetToken>")
                    field("newPassword", "anotherPassword123!", "새로 설정할 비밀번호")
                }
            }
            response {
                status(422)
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
    fun `새 비밀번호가 8자 미만이면 400을 반환한다`() {
        restDocs(mockMvc, "password-reset/confirm-invalid-password") {
            summary("비밀번호 재설정")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets/confirm")
                body {
                    field("passwordResetToken", "some-token", "재설정 토큰")
                    field("newPassword", "short", "8자 미만의 비밀번호")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `새 비밀번호에 특수문자가 없으면 400을 반환한다`() {
        restDocs(mockMvc, "password-reset/confirm-password-no-special-char") {
            summary("비밀번호 재설정")
            tag("PasswordReset")
            request {
                post("/api/v1/password-resets/confirm")
                body {
                    field("passwordResetToken", "some-token", "재설정 토큰")
                    field("newPassword", "newPassword123", "특수문자가 없는 비밀번호")
                }
            }
            response {
                status(400)
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    obj("error", "오류 정보") {
                        field("code", "오류 코드")
                        field("message", "오류 메시지")
                        array("details", "필드별 검증 오류 목록") {
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }
}
