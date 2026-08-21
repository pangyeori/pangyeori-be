package com.debate.pangyeori.auth.service

import com.debate.pangyeori.auth.domain.RefreshToken
import com.debate.pangyeori.auth.exception.PasswordResetTokenNotFoundException
import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.auth.repository.PasswordResetRedisRepository
import com.debate.pangyeori.auth.repository.RefreshTokenRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.enums.UserRole
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.exception.EmailNotVerifiedException
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder

class PasswordResetServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val emailVerificationRedisRepository = mockk<EmailVerificationRedisRepository>()
    val passwordResetRedisRepository = mockk<PasswordResetRedisRepository>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()

    val passwordResetService = PasswordResetService(
        userRepository = userRepository,
        emailVerificationRedisRepository = emailVerificationRedisRepository,
        passwordResetRedisRepository = passwordResetRedisRepository,
        refreshTokenRepository = refreshTokenRepository,
        passwordEncoder = passwordEncoder,
    )

    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val email = "user@pangyeori.com"
    val passwordResetToken = "password-reset-token"
    val newPassword = "newPassword123!"
    val encodedPassword = "encoded-new-password"

    fun activeUser() = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000001")
        .set(User::email, email)
        .set(User::role, UserRole.USER)
        .set(User::status, UserStatus.ACTIVE)
        .sample()

    beforeEach {
        clearMocks(
            userRepository,
            emailVerificationRedisRepository,
            passwordResetRedisRepository,
            refreshTokenRepository,
            passwordEncoder,
        )
    }

    Given("이메일 인증을 완료한 사용자가 재설정 토큰 발급을 요청하면") {
        When("가입된 이메일이면") {
            Then("재설정 토큰을 발급하고 인증 표식을 제거한다") {
                every {
                    emailVerificationRedisRepository.isVerified(
                        email = email,
                    )
                } returns true
                every {
                    userRepository.existsByEmail(
                        email = email,
                    )
                } returns true
                val tokenSlot = slot<String>()
                every {
                    passwordResetRedisRepository.saveToken(
                        email = email,
                        token = capture(tokenSlot),
                    )
                } just runs
                every {
                    emailVerificationRedisRepository.clearVerified(
                        email = email,
                    )
                } just runs

                val response = passwordResetService.issueToken(
                    email = email,
                )

                response.passwordResetToken shouldBe tokenSlot.captured
                verify {
                    emailVerificationRedisRepository.clearVerified(
                        email = email,
                    )
                }
            }
        }

        When("이메일 인증이 완료되지 않았으면") {
            Then("EmailNotVerifiedException을 던진다") {
                every {
                    emailVerificationRedisRepository.isVerified(
                        email = email,
                    )
                } returns false

                shouldThrow<EmailNotVerifiedException> {
                    passwordResetService.issueToken(
                        email = email,
                    )
                }
            }
        }

        When("가입된 사용자가 없으면") {
            Then("UserNotFoundException을 던진다") {
                every {
                    emailVerificationRedisRepository.isVerified(
                        email = email,
                    )
                } returns true
                every {
                    userRepository.existsByEmail(
                        email = email,
                    )
                } returns false

                shouldThrow<UserNotFoundException> {
                    passwordResetService.issueToken(
                        email = email,
                    )
                }
            }
        }
    }

    Given("발급된 재설정 토큰으로 비밀번호 재설정을 요청하면") {
        When("토큰이 유효하면") {
            Then("비밀번호를 변경하고 기존 세션을 모두 무효화하며 토큰을 삭제한다") {
                val user = activeUser()
                val activeToken = RefreshToken.create(
                    user = user,
                    token = "refresh-token",
                    expiresInSeconds = 1_209_600,
                )
                every {
                    passwordResetRedisRepository.findEmailByToken(
                        token = passwordResetToken,
                    )
                } returns email
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    passwordEncoder.encode(newPassword)
                } returns encodedPassword
                every {
                    refreshTokenRepository.findAllByUserAndRevokedAtIsNull(
                        user = user,
                    )
                } returns listOf(activeToken)
                every {
                    passwordResetRedisRepository.deleteToken(
                        token = passwordResetToken,
                    )
                } just runs

                passwordResetService.resetPassword(
                    passwordResetToken = passwordResetToken,
                    newPassword = newPassword,
                )

                user.password shouldBe encodedPassword
                activeToken.revokedAt.shouldNotBeNull()
                verify {
                    passwordResetRedisRepository.deleteToken(
                        token = passwordResetToken,
                    )
                }
            }
        }

        When("토큰이 존재하지 않거나 만료되었으면") {
            Then("PasswordResetTokenNotFoundException을 던진다") {
                every {
                    passwordResetRedisRepository.findEmailByToken(
                        token = passwordResetToken,
                    )
                } returns null

                shouldThrow<PasswordResetTokenNotFoundException> {
                    passwordResetService.resetPassword(
                        passwordResetToken = passwordResetToken,
                        newPassword = newPassword,
                    )
                }
            }
        }
    }
})
