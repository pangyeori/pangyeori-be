package com.debate.pangyeori.user.service

import com.debate.pangyeori.user.domain.RefreshToken
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.enums.UserRole
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.exception.InvalidCredentialsException
import com.debate.pangyeori.user.exception.InvalidTokenException
import com.debate.pangyeori.user.repository.RefreshTokenRepository
import com.debate.pangyeori.user.repository.UserRepository
import com.debate.pangyeori.user.token.TokenProvider
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder

class UserAuthServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val tokenProvider = mockk<TokenProvider>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()

    val userAuthService = UserAuthService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        tokenProvider = tokenProvider,
        refreshTokenRepository = refreshTokenRepository,
    )

    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    val email = "user@pangyeori.com"
    val password = "password123!"
    val encodedPassword = "encoded-password"
    val refreshToken = "refresh-token"
    val issuedTokens = TokenProvider.IssuedTokens(
        accessToken = "access-token",
        refreshToken = "rotated-refresh-token",
        accessTokenExpiresIn = 900,
        refreshTokenExpiresIn = 1_209_600,
    )

    fun activeUser() = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000001")
        .set(User::email, email)
        .set(User::password, encodedPassword)
        .set(User::role, UserRole.USER)
        .set(User::status, UserStatus.ACTIVE)
        .sample()

    beforeEach {
        clearMocks(
            userRepository,
            passwordEncoder,
            tokenProvider,
            refreshTokenRepository,
        )
    }

    Given("활성 사용자가 로그인을 요청하면") {
        When("이메일과 비밀번호가 일치하면") {
            Then("토큰을 발급하고 Refresh Token을 저장한다") {
                val user = activeUser()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    passwordEncoder.matches(password, encodedPassword)
                } returns true
                every {
                    tokenProvider.issue(
                        user = user,
                    )
                } returns issuedTokens
                every {
                    refreshTokenRepository.save(any())
                } answers { firstArg() }

                val response = userAuthService.signIn(
                    email = email,
                    password = password,
                )

                response.accessToken shouldBe issuedTokens.accessToken
                response.refreshToken shouldBe issuedTokens.refreshToken
                verify {
                    refreshTokenRepository.save(
                        match {
                            it.user == user &&
                                it.tokenHash == RefreshToken.hash(issuedTokens.refreshToken)
                        },
                    )
                }
            }
        }

        When("비밀번호가 일치하지 않으면") {
            Then("InvalidCredentialsException을 던진다") {
                val user = activeUser()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    passwordEncoder.matches(password, encodedPassword)
                } returns false

                shouldThrow<InvalidCredentialsException> {
                    userAuthService.signIn(
                        email = email,
                        password = password,
                    )
                }
            }
        }
    }

    Given("유효한 Refresh Token으로 재발급을 요청하면") {
        When("저장된 토큰과 사용자가 유효하면") {
            Then("기존 토큰을 소비하고 새 토큰을 발급한다") {
                val user = activeUser()
                val claims = TokenProvider.TokenClaims(
                    subject = email,
                    role = UserRole.USER.name,
                    tokenId = "0000000000002",
                )
                val savedToken = RefreshToken.create(
                    user = user,
                    token = refreshToken,
                    expiresInSeconds = issuedTokens.refreshTokenExpiresIn,
                )
                every {
                    tokenProvider.parseRefreshToken(
                        token = refreshToken,
                    )
                } returns claims
                every {
                    refreshTokenRepository.findByTokenHashForUpdate(
                        tokenHash = RefreshToken.hash(refreshToken),
                    )
                } returns savedToken
                every {
                    tokenProvider.issue(
                        user = user,
                    )
                } returns issuedTokens
                every {
                    refreshTokenRepository.save(any())
                } answers { firstArg() }

                val response = userAuthService.refresh(
                    refreshToken = refreshToken,
                )

                response.refreshToken shouldBe issuedTokens.refreshToken
                verify {
                    refreshTokenRepository.findByTokenHashForUpdate(
                        tokenHash = RefreshToken.hash(refreshToken),
                    )
                }
                savedToken.revokedAt.shouldNotBeNull()
            }
        }

        When("이미 소비된 토큰이면") {
            Then("InvalidTokenException을 던진다") {
                val claims = TokenProvider.TokenClaims(
                    subject = email,
                    role = UserRole.USER.name,
                    tokenId = "0000000000002",
                )
                every {
                    tokenProvider.parseRefreshToken(
                        token = refreshToken,
                    )
                } returns claims
                every {
                    refreshTokenRepository.findByTokenHashForUpdate(
                        tokenHash = RefreshToken.hash(refreshToken),
                    )
                } returns null

                shouldThrow<InvalidTokenException> {
                    userAuthService.refresh(
                        refreshToken = refreshToken,
                    )
                }
            }
        }
    }
})
