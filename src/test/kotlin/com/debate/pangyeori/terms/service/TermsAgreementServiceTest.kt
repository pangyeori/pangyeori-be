package com.debate.pangyeori.terms.service

import com.debate.pangyeori.terms.domain.Terms
import com.debate.pangyeori.terms.domain.UserTermsAgreement
import com.debate.pangyeori.terms.domain.enums.TermsType
import com.debate.pangyeori.terms.exception.RequiredTermsNotAgreedException
import com.debate.pangyeori.terms.exception.RequiredTermsWithdrawalNotAllowedException
import com.debate.pangyeori.terms.exception.TermsNotFoundException
import com.debate.pangyeori.terms.exception.TermsNotLatestException
import com.debate.pangyeori.terms.repository.TermsRepository
import com.debate.pangyeori.terms.repository.UserTermsAgreementRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class TermsAgreementServiceTest : BehaviorSpec({
    val termsRepository = mockk<TermsRepository>()
    val userTermsAgreementRepository = mockk<UserTermsAgreementRepository>()
    val userRepository = mockk<UserRepository>()

    val termsAgreementService = TermsAgreementService(
        termsRepository = termsRepository,
        userTermsAgreementRepository = userTermsAgreementRepository,
        userRepository = userRepository,
    )

    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val email = "user@pangyeori.com"
    val userId = "0000000000001"
    val serviceTermsId = "0000000000011"
    val privacyTermsId = "0000000000012"
    val marketingTermsId = "0000000000013"

    beforeEach {
        clearMocks(
            termsRepository,
            userTermsAgreementRepository,
            userRepository,
        )
    }

    fun user(): User = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, userId)
        .set(User::email, email)
        .sample()

    fun terms(
        id: String,
        type: TermsType,
        required: Boolean,
    ): Terms = fixtureMonkey.giveMeKotlinBuilder<Terms>()
        .set(Terms::id, id)
        .set(Terms::type, type)
        .set(Terms::required, required)
        .sample()

    fun serviceTerms() = terms(
        id = serviceTermsId,
        type = TermsType.TERMS_OF_SERVICE,
        required = true,
    )

    fun privacyTerms() = terms(
        id = privacyTermsId,
        type = TermsType.PRIVACY_POLICY,
        required = true,
    )

    fun marketingTerms() = terms(
        id = marketingTermsId,
        type = TermsType.MARKETING,
        required = false,
    )

    fun agreement(
        user: User,
        terms: Terms,
        withdrawn: Boolean = false,
    ): UserTermsAgreement = fixtureMonkey.giveMeKotlinBuilder<UserTermsAgreement>()
        .set(UserTermsAgreement::user, user)
        .set(UserTermsAgreement::terms, terms)
        .let {
            if (withdrawn) it else it.set(UserTermsAgreement::withdrawnAt, null)
        }
        .sample()

    fun stubLatestTerms(
        vararg latest: Terms,
    ) {
        every {
            termsRepository.findAllLatestEffective(
                currentTime = any(),
            )
        } returns latest.toList()
    }

    Given("회원가입 시 약관 동의 요청이 오면") {
        When("필수 약관과 선택 약관에 모두 동의하면") {
            Then("동의한 약관마다 동의 이력을 저장한다") {
                val user = user()
                val service = serviceTerms()
                val privacy = privacyTerms()
                val marketing = marketingTerms()
                val agreementsSlot = slot<Iterable<UserTermsAgreement>>()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, privacyTermsId, marketingTermsId))
                } returns listOf(service, privacy, marketing)
                stubLatestTerms(service, privacy, marketing)
                every {
                    userTermsAgreementRepository.saveAll(capture(agreementsSlot))
                } returns emptyList()

                termsAgreementService.agreeAtSignUp(
                    user = user,
                    agreedTermsIds = listOf(serviceTermsId, privacyTermsId, marketingTermsId),
                )

                val saved = agreementsSlot.captured.toList()
                saved.map { it.terms.id } shouldBe listOf(serviceTermsId, privacyTermsId, marketingTermsId)
                saved.all { it.user === user } shouldBe true
                saved.all { it.withdrawnAt == null } shouldBe true
            }
        }

        When("필수 약관에만 동의하면") {
            Then("선택 약관 이력 없이 필수 약관 이력만 저장한다") {
                val service = serviceTerms()
                val privacy = privacyTerms()
                val agreementsSlot = slot<Iterable<UserTermsAgreement>>()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, privacyTermsId))
                } returns listOf(service, privacy)
                stubLatestTerms(service, privacy, marketingTerms())
                every {
                    userTermsAgreementRepository.saveAll(capture(agreementsSlot))
                } returns emptyList()

                termsAgreementService.agreeAtSignUp(
                    user = user(),
                    agreedTermsIds = listOf(serviceTermsId, privacyTermsId),
                )

                agreementsSlot.captured.map { it.terms.id } shouldBe listOf(serviceTermsId, privacyTermsId)
            }
        }

        When("같은 약관 id가 중복되어 오면") {
            Then("중복을 제거하고 한 번만 저장한다") {
                val service = serviceTerms()
                val privacy = privacyTerms()
                val agreementsSlot = slot<Iterable<UserTermsAgreement>>()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, privacyTermsId))
                } returns listOf(service, privacy)
                stubLatestTerms(service, privacy)
                every {
                    userTermsAgreementRepository.saveAll(capture(agreementsSlot))
                } returns emptyList()

                termsAgreementService.agreeAtSignUp(
                    user = user(),
                    agreedTermsIds = listOf(serviceTermsId, privacyTermsId, serviceTermsId),
                )

                agreementsSlot.captured.map { it.terms.id } shouldBe listOf(serviceTermsId, privacyTermsId)
            }
        }

        When("존재하지 않는 약관 id가 포함되어 있으면") {
            Then("TermsNotFoundException을 던진다") {
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, "9999999999999"))
                } returns listOf(serviceTerms())

                shouldThrow<TermsNotFoundException> {
                    termsAgreementService.agreeAtSignUp(
                        user = user(),
                        agreedTermsIds = listOf(serviceTermsId, "9999999999999"),
                    )
                }

                verify(exactly = 0) {
                    userTermsAgreementRepository.saveAll(any<Iterable<UserTermsAgreement>>())
                }
            }
        }

        When("최신 버전이 아닌 약관에 동의하면") {
            Then("TermsNotLatestException을 던진다") {
                val oldService = serviceTerms()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId))
                } returns listOf(oldService)
                stubLatestTerms(
                    terms(
                        id = "0000000000021",
                        type = TermsType.TERMS_OF_SERVICE,
                        required = true,
                    ),
                )

                shouldThrow<TermsNotLatestException> {
                    termsAgreementService.agreeAtSignUp(
                        user = user(),
                        agreedTermsIds = listOf(serviceTermsId),
                    )
                }

                verify(exactly = 0) {
                    userTermsAgreementRepository.saveAll(any<Iterable<UserTermsAgreement>>())
                }
            }
        }

        When("필수 약관에 동의하지 않으면") {
            Then("RequiredTermsNotAgreedException을 던진다") {
                val service = serviceTerms()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId))
                } returns listOf(service)
                stubLatestTerms(service, privacyTerms(), marketingTerms())

                shouldThrow<RequiredTermsNotAgreedException> {
                    termsAgreementService.agreeAtSignUp(
                        user = user(),
                        agreedTermsIds = listOf(serviceTermsId),
                    )
                }

                verify(exactly = 0) {
                    userTermsAgreementRepository.saveAll(any<Iterable<UserTermsAgreement>>())
                }
            }
        }
    }

    Given("로그인한 사용자가 약관 동의 여부를 변경하면") {
        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns null

                shouldThrow<UserNotFoundException> {
                    termsAgreementService.changeAgreement(
                        email = email,
                        termsId = marketingTermsId,
                        agreed = true,
                    )
                }
            }
        }

        When("약관이 존재하지 않으면") {
            Then("TermsNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                every {
                    termsRepository.findById(marketingTermsId)
                } returns Optional.empty()

                shouldThrow<TermsNotFoundException> {
                    termsAgreementService.changeAgreement(
                        email = email,
                        termsId = marketingTermsId,
                        agreed = true,
                    )
                }
            }
        }

        When("최신 버전이 아닌 약관이면") {
            Then("TermsNotLatestException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                every {
                    termsRepository.findById(marketingTermsId)
                } returns Optional.of(marketingTerms())
                stubLatestTerms(
                    terms(
                        id = "0000000000023",
                        type = TermsType.MARKETING,
                        required = false,
                    ),
                )

                shouldThrow<TermsNotLatestException> {
                    termsAgreementService.changeAgreement(
                        email = email,
                        termsId = marketingTermsId,
                        agreed = true,
                    )
                }
            }
        }

        When("동의 이력이 없는 선택 약관에 동의하면") {
            Then("새 동의 이력을 저장한다") {
                val user = user()
                val marketing = marketingTerms()
                val agreementSlot = slot<UserTermsAgreement>()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    termsRepository.findById(marketingTermsId)
                } returns Optional.of(marketing)
                stubLatestTerms(marketing)
                every {
                    userTermsAgreementRepository.findByUserIdAndTermsId(
                        userId = userId,
                        termsId = marketingTermsId,
                    )
                } returns null
                every {
                    userTermsAgreementRepository.save(capture(agreementSlot))
                } answers { firstArg() }

                termsAgreementService.changeAgreement(
                    email = email,
                    termsId = marketingTermsId,
                    agreed = true,
                )

                agreementSlot.captured.user shouldBe user
                agreementSlot.captured.terms shouldBe marketing
                agreementSlot.captured.withdrawnAt.shouldBeNull()
            }
        }

        When("철회했던 선택 약관에 다시 동의하면") {
            Then("기존 동의 이력의 철회 시각을 비운다") {
                val user = user()
                val marketing = marketingTerms()
                val withdrawn = agreement(
                    user = user,
                    terms = marketing,
                    withdrawn = true,
                ).also {
                    it.withdraw()
                }
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    termsRepository.findById(marketingTermsId)
                } returns Optional.of(marketing)
                stubLatestTerms(marketing)
                every {
                    userTermsAgreementRepository.findByUserIdAndTermsId(
                        userId = userId,
                        termsId = marketingTermsId,
                    )
                } returns withdrawn

                termsAgreementService.changeAgreement(
                    email = email,
                    termsId = marketingTermsId,
                    agreed = true,
                )

                withdrawn.withdrawnAt.shouldBeNull()
                verify(exactly = 0) {
                    userTermsAgreementRepository.save(any())
                }
            }
        }

        When("동의 중인 선택 약관을 철회하면") {
            Then("같은 종류의 활성 동의 이력에 철회 시각을 기록한다") {
                val user = user()
                val marketing = marketingTerms()
                val active = agreement(
                    user = user,
                    terms = marketing,
                )
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    termsRepository.findById(marketingTermsId)
                } returns Optional.of(marketing)
                stubLatestTerms(marketing)
                every {
                    userTermsAgreementRepository.findAllByUserIdAndTermsTypeAndWithdrawnAtIsNull(
                        userId = userId,
                        termsType = TermsType.MARKETING,
                    )
                } returns listOf(active)

                termsAgreementService.changeAgreement(
                    email = email,
                    termsId = marketingTermsId,
                    agreed = false,
                )

                active.withdrawnAt.shouldNotBeNull()
            }
        }

        When("필수 약관을 철회하려고 하면") {
            Then("RequiredTermsWithdrawalNotAllowedException을 던진다") {
                val service = serviceTerms()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                every {
                    termsRepository.findById(serviceTermsId)
                } returns Optional.of(service)
                stubLatestTerms(service)

                shouldThrow<RequiredTermsWithdrawalNotAllowedException> {
                    termsAgreementService.changeAgreement(
                        email = email,
                        termsId = serviceTermsId,
                        agreed = false,
                    )
                }
            }
        }
    }

    Given("재동의가 필요한 약관 목록을 조회하면") {
        fun stubPendingIds(
            vararg ids: String,
        ) {
            every {
                termsRepository.findPendingRequiredIds(
                    email = email,
                    currentTime = any(),
                )
            } returns ids.toList()
        }

        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns null

                shouldThrow<UserNotFoundException> {
                    termsAgreementService.getPendingTerms(
                        email = email,
                    )
                }
            }
        }

        When("동의하지 않은 최신 필수 약관이 없으면") {
            Then("빈 목록을 반환한다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                stubPendingIds()
                every {
                    termsRepository.findAllById(emptyList<String>())
                } returns emptyList()

                termsAgreementService.getPendingTerms(
                    email = email,
                ) shouldBe emptyList()
            }
        }

        When("동의하지 않은 최신 필수 약관이 여러 개 있으면") {
            Then("TermsType 선언 순서대로 반환한다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                stubPendingIds(privacyTermsId, serviceTermsId)
                every {
                    termsRepository.findAllById(listOf(privacyTermsId, serviceTermsId))
                } returns listOf(privacyTerms(), serviceTerms())

                val response = termsAgreementService.getPendingTerms(
                    email = email,
                )

                response.map { it.termsId } shouldBe listOf(serviceTermsId, privacyTermsId)
            }
        }
    }

    Given("여러 약관에 한 번에 동의하면") {
        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns null

                shouldThrow<UserNotFoundException> {
                    termsAgreementService.agreeTerms(
                        email = email,
                        termsIds = listOf(serviceTermsId),
                    )
                }
            }
        }

        When("존재하지 않는 약관 id가 포함되어 있으면") {
            Then("TermsNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, privacyTermsId))
                } returns listOf(serviceTerms())

                shouldThrow<TermsNotFoundException> {
                    termsAgreementService.agreeTerms(
                        email = email,
                        termsIds = listOf(serviceTermsId, privacyTermsId),
                    )
                }
                verify(exactly = 0) {
                    userTermsAgreementRepository.save(any())
                }
            }
        }

        When("최신 버전이 아닌 약관이 포함되어 있으면") {
            Then("TermsNotLatestException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user()
                every {
                    termsRepository.findAllById(listOf(serviceTermsId))
                } returns listOf(serviceTerms())
                stubLatestTerms(privacyTerms())

                shouldThrow<TermsNotLatestException> {
                    termsAgreementService.agreeTerms(
                        email = email,
                        termsIds = listOf(serviceTermsId),
                    )
                }
                verify(exactly = 0) {
                    userTermsAgreementRepository.save(any())
                }
            }
        }

        When("동의 이력이 없는 약관과 철회했던 약관이 섞여 있으면") {
            Then("새 동의 이력을 저장하고 철회했던 이력의 철회 시각을 비운다") {
                val user = user()
                val service = serviceTerms()
                val marketing = marketingTerms()
                val withdrawn = agreement(
                    user = user,
                    terms = marketing,
                    withdrawn = true,
                ).also {
                    it.withdraw()
                }
                val agreementSlot = slot<UserTermsAgreement>()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    termsRepository.findAllById(listOf(serviceTermsId, marketingTermsId))
                } returns listOf(service, marketing)
                stubLatestTerms(service, marketing)
                every {
                    userTermsAgreementRepository.findByUserIdAndTermsId(
                        userId = userId,
                        termsId = serviceTermsId,
                    )
                } returns null
                every {
                    userTermsAgreementRepository.findByUserIdAndTermsId(
                        userId = userId,
                        termsId = marketingTermsId,
                    )
                } returns withdrawn
                every {
                    userTermsAgreementRepository.save(capture(agreementSlot))
                } answers { firstArg() }

                termsAgreementService.agreeTerms(
                    email = email,
                    termsIds = listOf(serviceTermsId, marketingTermsId, serviceTermsId),
                )

                agreementSlot.captured.terms shouldBe service
                withdrawn.withdrawnAt.shouldBeNull()
                verify(exactly = 1) {
                    userTermsAgreementRepository.save(any())
                }
            }
        }
    }

    Given("마케팅 수신 동의 여부를 조회하면") {
        When("철회하지 않은 마케팅 동의 이력이 있으면") {
            Then("true를 반환한다") {
                every {
                    userTermsAgreementRepository.existsByUserIdAndTermsTypeAndWithdrawnAtIsNull(
                        userId = userId,
                        termsType = TermsType.MARKETING,
                    )
                } returns true

                termsAgreementService.isMarketingAgreed(
                    userId = userId,
                ) shouldBe true
            }
        }

        When("동의 이력이 없거나 모두 철회했으면") {
            Then("false를 반환한다") {
                every {
                    userTermsAgreementRepository.existsByUserIdAndTermsTypeAndWithdrawnAtIsNull(
                        userId = userId,
                        termsType = TermsType.MARKETING,
                    )
                } returns false

                termsAgreementService.isMarketingAgreed(
                    userId = userId,
                ) shouldBe false
            }
        }
    }
})
