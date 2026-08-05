package com.debate.pangyeori.user.service

import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.EmailAlreadyExistsException
import com.debate.pangyeori.user.exception.EmailNotVerifiedException
import com.debate.pangyeori.user.exception.NicknameAlreadyExistsException
import com.debate.pangyeori.user.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder

class UserServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>(
        relaxed = true,
    )
    val emailVerificationRedisRepository = mockk<EmailVerificationRedisRepository>(
        relaxed = true,
    )
    val passwordEncoder = mockk<PasswordEncoder>()
    val userService = UserService(
        userRepository = userRepository,
        emailVerificationRedisRepository = emailVerificationRedisRepository,
        passwordEncoder = passwordEncoder,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val email = "user@pangyeori.com"
    val password = "password123!"
    val nickname = "판결이"

    beforeEach {
        clearMocks(
            userRepository,
            emailVerificationRedisRepository,
            passwordEncoder,
        )
    }

    Given("이메일 인증을 완료한 사용자의 회원가입 요청이 오면") {
        When("이메일과 닉네임이 중복되지 않으면") {
            Then("비밀번호를 해시하여 회원을 저장하고 인증 표식을 제거한다") {
                val userSlot = slot<User>()
                val savedUser = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000001")
                    .set(User::email, email)
                    .set(User::nickname, nickname)
                    .sample()

                every {
                    emailVerificationRedisRepository.isVerified(
                        email = email,
                    )
                } returns true
                every {
                    passwordEncoder.encode(password)
                } returns "encoded-password"
                every {
                    userRepository.save(capture(userSlot))
                } returns savedUser
                every {
                    emailVerificationRedisRepository.clearVerified(
                        email = email,
                    )
                } just runs

                val response = userService.createUser(
                    email = email,
                    password = password,
                    nickname = nickname,
                    profileImageUrl = null,
                )

                userSlot.captured.password shouldBe "encoded-password"
                response.id shouldBe "0000000000001"
                verify {
                    emailVerificationRedisRepository.clearVerified(
                        email = email,
                    )
                }
            }
        }
    }

    Given("회원가입 요청의 이메일 인증 여부를 확인할 때") {
        When("인증되지 않은 이메일이면") {
            Then("EmailNotVerifiedException을 던진다") {
                every {
                    emailVerificationRedisRepository.isVerified(
                        email = email,
                    )
                } returns false

                shouldThrow<EmailNotVerifiedException> {
                    userService.createUser(
                        email = email,
                        password = password,
                        nickname = nickname,
                        profileImageUrl = null,
                    )
                }
            }
        }
    }

    Given("인증된 이메일로 회원가입 요청이 오면") {
        beforeEach {
            every {
                emailVerificationRedisRepository.isVerified(
                    email = email,
                )
            } returns true
        }

        When("이미 가입된 이메일이면") {
            Then("EmailAlreadyExistsException을 던진다") {
                every {
                    userRepository.existsByEmail(
                        email = email,
                    )
                } returns true

                shouldThrow<EmailAlreadyExistsException> {
                    userService.createUser(
                        email = email,
                        password = password,
                        nickname = nickname,
                        profileImageUrl = null,
                    )
                }
            }
        }

        When("이미 사용 중인 닉네임이면") {
            Then("NicknameAlreadyExistsException을 던진다") {
                every {
                    userRepository.existsByNickname(
                        nickname = nickname,
                    )
                } returns true

                shouldThrow<NicknameAlreadyExistsException> {
                    userService.createUser(
                        email = email,
                        password = password,
                        nickname = nickname,
                        profileImageUrl = null,
                    )
                }
            }
        }
    }
})
