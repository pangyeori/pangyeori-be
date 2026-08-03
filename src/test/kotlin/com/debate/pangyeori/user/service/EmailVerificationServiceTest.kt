package com.debate.pangyeori.user.service

import com.debate.pangyeori.email.EmailSender
import com.debate.pangyeori.user.exception.EmailVerificationAlreadyVerifiedException
import com.debate.pangyeori.user.exception.EmailVerificationCodeMismatchException
import com.debate.pangyeori.user.exception.EmailVerificationCodeNotFoundException
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldMatch
import io.mockk.*

class EmailVerificationServiceTest : BehaviorSpec({

    val emailVerificationRedisRepository = mockk<EmailVerificationRedisRepository>(
        relaxed = true,
    )
    val emailSender = mockk<EmailSender>(
        relaxed = true,
    )
    val emailVerificationService = EmailVerificationService(
        emailVerificationRedisRepository = emailVerificationRedisRepository,
        emailSender = emailSender,
    )

    val email = "user@pangyeori.com"

    Given("이메일 인증 코드 발송 요청이 오면") {
        When("sendCode를 호출하면") {
            Then("6자리 코드를 생성해 저장하고 이메일로 발송한다") {
                val codeSlot = slot<String>()
                every {
                    emailVerificationRedisRepository.saveCode(
                        email = email,
                        code = capture(codeSlot),
                    )
                } just Runs

                emailVerificationService.sendCode(
                    email = email,
                )

                codeSlot.captured shouldMatch "^[0-9]{6}$"
                verify {
                    emailVerificationRedisRepository.saveCode(
                        email = email,
                        code = codeSlot.captured,
                    )
                }
                verify {
                    emailSender.send(
                        to = email,
                        subject = any(),
                        content = match { it.contains(codeSlot.captured) },
                    )
                }
            }
        }
    }

    Given("이메일 인증 코드 검증 요청이 오면") {
        When("저장된 코드와 일치하면") {
            Then("코드를 삭제하고 인증 완료 처리한다") {
                every { emailVerificationRedisRepository.findCode(email) } returns "123456"

                emailVerificationService.confirmCode(
                    email = email,
                    code = "123456",
                )

                verify { emailVerificationRedisRepository.deleteCode(email) }
                verify { emailVerificationRedisRepository.markVerified(email) }
            }
        }

        When("코드가 일치하지 않으면") {
            Then("EmailVerificationCodeMismatchException을 던진다") {
                every { emailVerificationRedisRepository.findCode(email) } returns "123456"

                shouldThrow<EmailVerificationCodeMismatchException> {
                    emailVerificationService.confirmCode(
                        email = email,
                        code = "999999",
                    )
                }
            }
        }

        When("저장된 코드가 없으면") {
            Then("EmailVerificationCodeNotFoundException을 던진다") {
                every { emailVerificationRedisRepository.findCode(email) } returns null

                shouldThrow<EmailVerificationCodeNotFoundException> {
                    emailVerificationService.confirmCode(
                        email = email,
                        code = "123456",
                    )
                }
            }
        }

        When("이미 인증된 이메일이면") {
            Then("EmailVerificationAlreadyVerifiedException을 던진다") {
                every { emailVerificationRedisRepository.isVerified(email) } returns true

                shouldThrow<EmailVerificationAlreadyVerifiedException> {
                    emailVerificationService.confirmCode(
                        email = email,
                        code = "123456",
                    )
                }
            }
        }
    }
})
