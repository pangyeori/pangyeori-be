package com.debate.pangyeori.auth.service

import com.debate.pangyeori.auth.exception.EmailVerificationAlreadyVerifiedException
import com.debate.pangyeori.auth.exception.EmailVerificationCodeMismatchException
import com.debate.pangyeori.auth.exception.EmailVerificationCodeNotFoundException
import com.debate.pangyeori.auth.exception.EmailVerificationRateLimitedException
import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.email.sender.EmailSender
import com.debate.pangyeori.email.exception.EmailSendFailedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldMatch
import io.mockk.*
import org.springframework.mail.MailSendException

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
                every {
                    emailVerificationRedisRepository.trySaveRateLimit(email)
                } returns true

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

        When("이메일 발송이 재시도 후에도 계속 실패하면") {
            Then("EmailSendFailedException을 던진다") {
                every {
                    emailVerificationRedisRepository.trySaveRateLimit(email)
                } returns true
                every {
                    emailSender.send(
                        to = email,
                        subject = any(),
                        content = any(),
                    )
                } throws MailSendException("발송 실패")

                shouldThrow<EmailSendFailedException> {
                    emailVerificationService.sendCode(
                        email = email,
                    )
                }
            }
        }

        When("이미 발송 요청을 처리한 이메일로 다시 요청하면") {
            Then("EmailVerificationRateLimitedException을 던진다") {
                every {
                    emailVerificationRedisRepository.trySaveRateLimit(email)
                } returns false

                shouldThrow<EmailVerificationRateLimitedException> {
                    emailVerificationService.sendCode(
                        email = email,
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
            Then("EmailVerificationCodeMismatchException을 던지고 시도 횟수를 증가시킨다") {
                every { emailVerificationRedisRepository.findCode(email) } returns "123456"
                every { emailVerificationRedisRepository.incrementAttempt(email) } returns 1L

                shouldThrow<EmailVerificationCodeMismatchException> {
                    emailVerificationService.confirmCode(
                        email = email,
                        code = "999999",
                    )
                }

                verify { emailVerificationRedisRepository.incrementAttempt(email) }
            }
        }

        When("코드 불일치가 최대 시도 횟수에 도달하면") {
            Then("EmailVerificationCodeMismatchException을 던지고 코드를 무효화한다") {
                every { emailVerificationRedisRepository.findCode(email) } returns "123456"
                every { emailVerificationRedisRepository.incrementAttempt(email) } returns 5L

                shouldThrow<EmailVerificationCodeMismatchException> {
                    emailVerificationService.confirmCode(
                        email = email,
                        code = "999999",
                    )
                }

                verify { emailVerificationRedisRepository.deleteCode(email) }
                verify { emailVerificationRedisRepository.resetAttempt(email) }
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
