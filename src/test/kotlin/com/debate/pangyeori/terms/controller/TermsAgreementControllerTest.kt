package com.debate.pangyeori.terms.controller

import com.debate.pangyeori.auth.service.AuthService
import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.ResponseBodyDsl
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.UserTermsAgreement
import com.debate.pangyeori.terms.domain.enums.TermsType
import com.debate.pangyeori.terms.repository.TermsRepository
import com.debate.pangyeori.terms.repository.UserTermsAgreementRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset

class TermsAgreementControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var termsRepository: TermsRepository

    @Autowired
    private lateinit var userTermsAgreementRepository: UserTermsAgreementRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    private fun createUser(
        email: String,
    ) = userRepository.save(
        User.create(
            email = email,
            password = passwordEncoder.encode("password123!")!!,
            nickname = "약관사용자",
        ),
    )

    private fun issueAccessToken(
        email: String,
    ) = authService.signIn(
        email = email,
        password = "password123!",
    ).accessToken

    private fun createTerms(
        type: TermsType,
        version: Int = 1,
        title: String,
        required: Boolean,
    ) = termsRepository.save(
        Terms.create(
            type = type,
            version = version,
            title = title,
            content = "$title 본문입니다.",
            required = required,
            effectiveAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
        ),
    )

    private val errorBody: ResponseBodyDsl.() -> Unit = {
        field("success", "처리 성공 여부")
        field("data", "응답 데이터").optional()
        obj("error", "에러 정보") {
            field("code", "에러 코드")
            field("message", "에러 메시지")
            field("details", "필드별 검증 오류").optional()
        }
    }

    @Test
    fun `마케팅 수신에 동의한다`() {
        val email = "agree-marketing@pangyeori.com"
        createUser(
            email = email,
        )
        val marketing = createTerms(
            type = TermsType.MARKETING,
            title = "마케팅 수신 동의",
            required = false,
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/change") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", marketing.id!!, "동의 여부를 변경할 약관 ID")
                }
                body {
                    field("agreed", true, "동의 여부 (철회하려면 false)")
                }
            }
            response {
                status(204)
            }
        }
    }

    @Test
    fun `동의했던 마케팅 수신을 철회한다`() {
        val email = "withdraw-marketing@pangyeori.com"
        val user = createUser(
            email = email,
        )
        val marketing = createTerms(
            type = TermsType.MARKETING,
            title = "마케팅 수신 동의",
            required = false,
        )
        userTermsAgreementRepository.save(
            UserTermsAgreement.create(
                user = user,
                terms = marketing,
            ),
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/withdraw") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", marketing.id!!, "동의 여부를 변경할 약관 ID")
                }
                body {
                    field("agreed", false, "동의 여부 (철회하려면 false)")
                }
            }
            response {
                status(204)
            }
        }
    }

    @Test
    fun `필수 약관을 철회하면 422를 반환한다`() {
        val email = "withdraw-required@pangyeori.com"
        val user = createUser(
            email = email,
        )
        val service = createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            title = "이용약관",
            required = true,
        )
        userTermsAgreementRepository.save(
            UserTermsAgreement.create(
                user = user,
                terms = service,
            ),
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/withdraw-required") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", service.id!!, "필수 약관 ID")
                }
                body {
                    field("agreed", false, "필수 약관을 철회하려는 요청")
                }
            }
            response {
                status(422)
                body(errorBody)
            }
        }
    }

    @Test
    fun `최신 버전이 아닌 약관의 동의 여부를 변경하면 422를 반환한다`() {
        val email = "change-old-terms@pangyeori.com"
        createUser(
            email = email,
        )
        val oldMarketing = createTerms(
            type = TermsType.MARKETING,
            version = 1,
            title = "마케팅 수신 동의",
            required = false,
        )
        createTerms(
            type = TermsType.MARKETING,
            version = 2,
            title = "마케팅 수신 동의",
            required = false,
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/change-not-latest") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", oldMarketing.id!!, "개정 전 버전의 약관 ID")
                }
                body {
                    field("agreed", true, "동의 여부")
                }
            }
            response {
                status(422)
                body(errorBody)
            }
        }
    }

    @Test
    fun `존재하지 않는 약관의 동의 여부를 변경하면 404를 반환한다`() {
        val email = "change-unknown-terms@pangyeori.com"
        createUser(
            email = email,
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/change-not-found") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", "0000000000000", "존재하지 않는 약관 ID")
                }
                body {
                    field("agreed", true, "동의 여부")
                }
            }
            response {
                status(404)
                body(errorBody)
            }
        }
    }

    @Test
    fun `동의 여부가 누락되면 400을 반환한다`() {
        val email = "change-missing-agreed@pangyeori.com"
        createUser(
            email = email,
        )
        val marketing = createTerms(
            type = TermsType.MARKETING,
            title = "마케팅 수신 동의",
            required = false,
        )
        val accessToken = issueAccessToken(
            email = email,
        )

        restDocs(mockMvc, "terms-agreements/change-invalid-input") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                header("Authorization", "Bearer $accessToken")
                pathParameters {
                    param("termsId", marketing.id!!, "동의 여부를 변경할 약관 ID")
                }
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

    @Test
    fun `인증 없이 약관 동의 여부를 변경하면 401을 반환한다`() {
        restDocs(mockMvc, "terms-agreements/change-unauthorized") {
            summary("약관 동의 여부 변경")
            tag("Terms")
            request {
                put("/api/v1/users/me/term-agreements/{termsId}")
                pathParameters {
                    param("termsId", "0000000000000", "동의 여부를 변경할 약관 ID")
                }
                body {
                    field("agreed", true, "동의 여부")
                }
            }
            response {
                status(401)
                body(errorBody)
            }
        }
    }
}
