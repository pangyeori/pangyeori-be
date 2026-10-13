package com.debate.pangyeori.terms.controller

import com.debate.pangyeori.support.restdocs.RestDocsMvcTest
import com.debate.pangyeori.support.restdocs.dsl.restDocs
import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.enums.TermsType
import com.debate.pangyeori.terms.repository.TermsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.ZoneOffset

class TermsControllerTest : RestDocsMvcTest() {

    @Autowired
    private lateinit var termsRepository: TermsRepository

    private fun createTerms(
        type: TermsType,
        version: Int,
        title: String,
        required: Boolean,
    ) = termsRepository.save(
        Terms.create(
            type = type,
            version = version,
            title = title,
            content = "$title 본문입니다. (v$version)",
            required = required,
            effectiveAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
        ),
    )

    @Test
    fun `타입별 최신 약관 목록을 조회한다`() {
        createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 1,
            title = "이용약관",
            required = true,
        )
        createTerms(
            type = TermsType.TERMS_OF_SERVICE,
            version = 2,
            title = "이용약관",
            required = true,
        )
        createTerms(
            type = TermsType.PRIVACY_POLICY,
            version = 1,
            title = "개인정보 수집·이용 동의",
            required = true,
        )
        createTerms(
            type = TermsType.MARKETING,
            version = 1,
            title = "마케팅 수신 동의",
            required = false,
        )

        restDocs(mockMvc, "terms/get-latest-terms") {
            summary("최신 약관 목록 조회")
            tag("Terms")
            request {
                get("/api/v1/terms")
            }
            response {
                status(200)
                body {
                    field("success", "처리 성공 여부")
                    array("data", "타입별 최신 버전 약관 목록. 이용약관은 개정된 v2만 내려온다") {
                        field("termsId", "약관 ID")
                        field("type", "약관 종류 (TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING)")
                        field("version", "약관 버전")
                        field("title", "약관 제목")
                        field("content", "약관 본문 (Markdown)")
                        field("required", "필수 동의 여부")
                        field("effectiveAt", "시행일")
                    }
                    field("error", "오류 정보").optional()
                }
            }
        }
    }
}
