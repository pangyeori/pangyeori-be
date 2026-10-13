package com.debate.pangyeori.terms.service

import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.enums.TermsType
import com.debate.pangyeori.terms.repository.TermsRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk

class TermsServiceTest : BehaviorSpec({
    val termsRepository = mockk<TermsRepository>()

    val termsService = TermsService(
        termsRepository = termsRepository,
    )

    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    beforeEach {
        clearMocks(termsRepository)
    }

    Given("약관 목록 조회 요청이 오면") {
        When("시행 중인 약관이 있으면") {
            Then("타입별 최신 약관을 본문과 함께 반환한다") {
                val termsOfService = fixtureMonkey.giveMeKotlinBuilder<Terms>()
                    .set(Terms::id, "0000000000011")
                    .set(Terms::type, TermsType.TERMS_OF_SERVICE)
                    .set(Terms::required, true)
                    .sample()
                val marketing = fixtureMonkey.giveMeKotlinBuilder<Terms>()
                    .set(Terms::id, "0000000000012")
                    .set(Terms::type, TermsType.MARKETING)
                    .set(Terms::required, false)
                    .sample()
                every {
                    termsRepository.findAllLatestEffective(
                        currentTime = any(),
                    )
                } returns listOf(termsOfService, marketing)

                val response = termsService.getLatestTerms()

                response.map { it.termsId } shouldBe listOf("0000000000011", "0000000000012")
                response[0].type shouldBe TermsType.TERMS_OF_SERVICE
                response[0].content shouldBe termsOfService.content
                response[0].required shouldBe true
                response[1].type shouldBe TermsType.MARKETING
                response[1].required shouldBe false
            }
        }

        When("저장된 순서와 TermsType 선언 순서가 다르면") {
            Then("TermsType 선언 순서대로 반환한다") {
                val marketing = fixtureMonkey.giveMeKotlinBuilder<Terms>()
                    .set(Terms::id, "0000000000021")
                    .set(Terms::type, TermsType.MARKETING)
                    .sample()
                val privacyPolicy = fixtureMonkey.giveMeKotlinBuilder<Terms>()
                    .set(Terms::id, "0000000000022")
                    .set(Terms::type, TermsType.PRIVACY_POLICY)
                    .sample()
                val termsOfService = fixtureMonkey.giveMeKotlinBuilder<Terms>()
                    .set(Terms::id, "0000000000023")
                    .set(Terms::type, TermsType.TERMS_OF_SERVICE)
                    .sample()
                every {
                    termsRepository.findAllLatestEffective(
                        currentTime = any(),
                    )
                } returns listOf(marketing, privacyPolicy, termsOfService)

                val response = termsService.getLatestTerms()

                response.map { it.type } shouldBe listOf(
                    TermsType.TERMS_OF_SERVICE,
                    TermsType.PRIVACY_POLICY,
                    TermsType.MARKETING,
                )
            }
        }

        When("시행 중인 약관이 없으면") {
            Then("빈 목록을 반환한다") {
                every {
                    termsRepository.findAllLatestEffective(
                        currentTime = any(),
                    )
                } returns emptyList()

                val response = termsService.getLatestTerms()

                response shouldBe emptyList()
            }
        }
    }
})
