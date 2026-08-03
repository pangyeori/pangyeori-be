package com.debate.pangyeori.user.controller

import com.debate.pangyeori.email.TestEmailSenderConfig
import com.debate.pangyeori.support.RestDocsMvcTest
import com.debate.pangyeori.support.dsl.restDocs
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.user.service.EmailVerificationService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(TestEmailSenderConfig::class)
class EmailVerificationControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var emailVerificationService: EmailVerificationService

    @Autowired
    private lateinit var emailVerificationRedisRepository: EmailVerificationRedisRepository

    @Test
    fun `인증 코드 발송에 성공한다`() {
        val email = "send-success@pangyeori.com"

        restDocs(mockMvc, "email-verification/send") {
            summary("이메일 인증 코드 발송")
            request {
                post("/api/v1/email-verifications")
                body {
                    field("email", email, "인증 코드를 받을 이메일")
                }
            }
            response {
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    field("error", "에러 정보").optional()
                }
            }
        }
    }

    @Test
    fun `이메일 형식이 올바르지 않으면 400을 반환한다`() {
        restDocs(mockMvc, "email-verification/send-invalid-email") {
            summary("이메일 인증 코드 발송")
            request {
                post("/api/v1/email-verifications")
                body {
                    field("email", "invalid-email", "형식이 잘못된 이메일")
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
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `발송 시 이메일 필드가 누락되면 400을 반환한다`() {
        restDocs(mockMvc, "email-verification/send-missing-email") {
            summary("이메일 인증 코드 발송")
            request {
                post("/api/v1/email-verifications")
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
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `발송된 코드로 인증에 성공한다`() {
        val email = "confirm-success@pangyeori.com"
        emailVerificationService.sendCode(
            email = email,
        )
        val code = requireNotNull(emailVerificationRedisRepository.findCode(email))

        restDocs(mockMvc, "email-verification/confirm") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", email, "인증 대상 이메일")
                    field("code", code, "발송된 6자리 인증 코드")
                }
            }
            response {
                body {
                    field("success", "처리 성공 여부")
                    field("data", "응답 데이터").optional()
                    field("error", "에러 정보").optional()
                }
            }
        }
    }

    @Test
    fun `인증 코드 형식이 올바르지 않으면 400을 반환한다`() {
        restDocs(mockMvc, "email-verification/confirm-invalid-code") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", "confirm-invalid-code@pangyeori.com", "인증 대상 이메일")
                    field("code", "abc", "형식이 잘못된 인증 코드")
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
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `검증 시 인증 코드 필드가 누락되면 400을 반환한다`() {
        restDocs(mockMvc, "email-verification/confirm-missing-code") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", "missing-code@pangyeori.com", "인증 대상 이메일")
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
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `검증 시 이메일 필드가 누락되면 400을 반환한다`() {
        restDocs(mockMvc, "email-verification/confirm-missing-email") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("code", "123456", "인증 코드")
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
                            field("field", "오류가 발생한 필드명")
                            field("message", "필드 오류 메시지")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `코드가 일치하지 않으면 422를 반환한다`() {
        val email = "confirm-mismatch@pangyeori.com"
        emailVerificationService.sendCode(
            email = email,
        )

        restDocs(mockMvc, "email-verification/confirm-mismatch") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", email, "인증 대상 이메일")
                    field("code", "000000", "잘못된 인증 코드")
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
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `발송된 적 없는 이메일로 검증하면 422를 반환한다`() {
        restDocs(mockMvc, "email-verification/confirm-not-found") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", "never-sent@pangyeori.com", "코드를 발송한 적 없는 이메일")
                    field("code", "123456", "인증 코드")
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
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }

    @Test
    fun `이미 인증된 이메일로 다시 검증하면 409를 반환한다`() {
        val email = "confirm-already-verified@pangyeori.com"
        emailVerificationService.sendCode(
            email = email,
        )
        val code = requireNotNull(emailVerificationRedisRepository.findCode(email))
        emailVerificationService.confirmCode(
            email = email,
            code = code,
        )

        restDocs(mockMvc, "email-verification/confirm-already-verified") {
            summary("이메일 인증 코드 검증")
            request {
                post("/api/v1/email-verifications/confirm")
                body {
                    field("email", email, "인증 대상 이메일")
                    field("code", code, "이미 사용된 인증 코드")
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
                        field("details", "필드별 검증 오류 목록").optional()
                    }
                }
            }
        }
    }
}
