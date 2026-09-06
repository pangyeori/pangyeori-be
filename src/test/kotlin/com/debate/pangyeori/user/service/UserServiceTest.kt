package com.debate.pangyeori.user.service

import com.debate.pangyeori.auth.domain.RefreshToken
import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.auth.repository.RefreshTokenRepository
import com.debate.pangyeori.storage.client.ObjectStorage
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.domain.enums.UserStatus
import com.debate.pangyeori.user.exception.EmailAlreadyExistsException
import com.debate.pangyeori.user.exception.EmailNotVerifiedException
import com.debate.pangyeori.user.exception.InvalidCurrentPasswordException
import com.debate.pangyeori.user.exception.NicknameAlreadyExistsException
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.security.crypto.password.PasswordEncoder
import software.amazon.awssdk.core.exception.SdkClientException

class UserServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val emailVerificationRedisRepository = mockk<EmailVerificationRedisRepository>()
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val objectStorage = mockk<ObjectStorage>(relaxUnitFun = true)
    val passwordEncoder = mockk<PasswordEncoder>()

    val userService = UserService(
        userRepository = userRepository,
        emailVerificationRedisRepository = emailVerificationRedisRepository,
        refreshTokenRepository = refreshTokenRepository,
        objectStorage = objectStorage,
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
            refreshTokenRepository,
            objectStorage,
            passwordEncoder,
        )
    }

    fun activeUser(
        currentNickname: String = nickname,
        currentPassword: String = "encoded-password",
        currentProfileImageKey: String? = null,
    ): User = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000001")
        .set(User::email, email)
        .set(User::nickname, currentNickname)
        .set(User::password, currentPassword)
        .set(User::profileImageKey, currentProfileImageKey)
        .set(User::status, UserStatus.ACTIVE)
        .sample()
        .let(::setAuditFields)

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
                    userRepository.existsByEmail(
                        email = email,
                    )
                } returns false
                every {
                    userRepository.existsByNickname(
                        nickname = nickname,
                    )
                } returns false
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

                userService.createUser(
                    email = email,
                    password = password,
                    nickname = nickname,
                )

                userSlot.captured.password shouldBe "encoded-password"
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
                    )
                }
            }
        }

        When("이미 사용 중인 닉네임이면") {
            Then("NicknameAlreadyExistsException을 던진다") {
                every {
                    userRepository.existsByEmail(
                        email = email,
                    )
                } returns false
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
                    )
                }
            }
        }
    }

    Given("닉네임 중복 여부를 확인할 때") {
        When("이미 사용 중인 닉네임이면") {
            Then("중복 여부로 true를 반환한다") {
                every {
                    userRepository.existsByNickname(
                        nickname = nickname,
                    )
                } returns true

                val response = userService.checkNicknameDuplicate(
                    nickname = nickname,
                )

                response.duplicated shouldBe true
            }
        }

        When("사용 가능한 닉네임이면") {
            Then("중복 여부로 false를 반환한다") {
                every {
                    userRepository.existsByNickname(
                        nickname = nickname,
                    )
                } returns false

                val response = userService.checkNicknameDuplicate(
                    nickname = nickname,
                )

                response.duplicated shouldBe false
            }
        }
    }

    Given("로그인한 사용자가 내 정보를 조회하면") {
        When("사용자가 존재하면") {
            Then("비밀번호를 제외한 사용자 정보를 반환한다") {
                val user = fixtureMonkey.giveMeKotlinBuilder<User>()
                    .set(User::id, "0000000000001")
                    .set(User::email, email)
                    .set(User::nickname, nickname)
                    .sample()
                    .let(::setAuditFields)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user

                val response = userService.getMyInfo(
                    email = email,
                )

                response.id shouldBe user.id
                response.email shouldBe user.email
                response.nickname shouldBe user.nickname
                response.profileImageKey shouldBe user.profileImageKey
                response.joinedAt shouldBe user.createdAt
            }
        }

        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns null

                shouldThrow<UserNotFoundException> {
                    userService.getMyInfo(
                        email = email,
                    )
                }
            }
        }
    }

    Given("로그인한 사용자가 회원정보를 수정할 때") {
        When("새 닉네임이 중복되지 않으면") {
            Then("닉네임을 변경하고 갱신된 정보를 반환한다") {
                val user = activeUser()
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    userRepository.existsByNickname(
                        nickname = "새로운닉네임",
                    )
                } returns false

                val response = userService.updateProfile(
                    email = email,
                    nickname = "새로운닉네임",
                    profileImageKey = null,
                )

                user.nickname shouldBe "새로운닉네임"
                response.nickname shouldBe "새로운닉네임"
            }
        }

        When("새 닉네임이 이미 사용 중이면") {
            Then("NicknameAlreadyExistsException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns activeUser()
                every {
                    userRepository.existsByNickname(
                        nickname = "중복닉네임",
                    )
                } returns true

                shouldThrow<NicknameAlreadyExistsException> {
                    userService.updateProfile(
                        email = email,
                        nickname = "중복닉네임",
                        profileImageKey = null,
                    )
                }
            }
        }

        When("현재 닉네임과 동일한 값이면") {
            Then("중복 검증 없이 통과한다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns activeUser(currentNickname = nickname)

                userService.updateProfile(
                    email = email,
                    nickname = nickname,
                    profileImageKey = null,
                )

                verify(exactly = 0) {
                    userRepository.existsByNickname(nickname = any())
                }
            }
        }

        When("기존 프로필 이미지가 없는 상태에서 새 키가 주어지면") {
            Then("프로필 이미지 키를 변경하고 S3 삭제는 호출하지 않는다") {
                val user = activeUser(currentProfileImageKey = null)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user

                userService.updateProfile(
                    email = email,
                    nickname = null,
                    profileImageKey = "profile-images/2026/09/0000000000001.png",
                )

                user.profileImageKey shouldBe "profile-images/2026/09/0000000000001.png"
                verify(exactly = 0) {
                    objectStorage.deleteObject(objectKey = any())
                }
            }
        }

        When("기존 프로필 이미지와 다른 새 키가 주어지면") {
            Then("프로필 이미지 키를 변경하고 이전 객체를 S3에서 삭제한다") {
                val previousKey = "profile-images/2026/08/0000000000001.png"
                val user = activeUser(currentProfileImageKey = previousKey)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user

                userService.updateProfile(
                    email = email,
                    nickname = null,
                    profileImageKey = "profile-images/2026/09/0000000000001.png",
                )

                user.profileImageKey shouldBe "profile-images/2026/09/0000000000001.png"
                verify {
                    objectStorage.deleteObject(objectKey = previousKey)
                }
            }
        }
    }

    Given("로그인한 사용자가 프로필 이미지를 제거할 때") {
        When("프로필 이미지가 있으면") {
            Then("profileImageKey를 null로 만들고 이전 객체를 S3에서 삭제한다") {
                val previousKey = "profile-images/2026/09/0000000000001.png"
                val user = activeUser(currentProfileImageKey = previousKey)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user

                userService.removeProfileImage(
                    email = email,
                )

                user.profileImageKey shouldBe null
                verify {
                    objectStorage.deleteObject(objectKey = previousKey)
                }
            }
        }

        When("프로필 이미지가 없으면") {
            Then("S3 삭제를 호출하지 않는다") {
                val user = activeUser(currentProfileImageKey = null)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user

                userService.removeProfileImage(
                    email = email,
                )

                user.profileImageKey shouldBe null
                verify(exactly = 0) {
                    objectStorage.deleteObject(objectKey = any())
                }
            }
        }

        When("S3 객체 삭제가 실패해도") {
            Then("예외를 전파하지 않고 profileImageKey를 null로 유지한다") {
                val previousKey = "profile-images/2026/09/0000000000001.png"
                val user = activeUser(currentProfileImageKey = previousKey)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    objectStorage.deleteObject(objectKey = previousKey)
                } throws SdkClientException.builder().message("S3 연결 실패").build()

                userService.removeProfileImage(
                    email = email,
                )

                user.profileImageKey shouldBe null
            }
        }
    }

    Given("로그인한 사용자가 비밀번호를 변경할 때") {
        When("현재 비밀번호가 일치하지 않으면") {
            Then("InvalidCurrentPasswordException을 던진다") {
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns activeUser(currentPassword = "encoded-current")
                every {
                    passwordEncoder.matches("wrong", "encoded-current")
                } returns false

                shouldThrow<InvalidCurrentPasswordException> {
                    userService.changePassword(
                        email = email,
                        currentPassword = "wrong",
                        newPassword = "newPassword1!",
                    )
                }
            }
        }

        When("현재 비밀번호가 일치하면") {
            Then("비밀번호를 새 해시로 바꾸고 모든 refresh token을 revoke한다") {
                val user = activeUser(currentPassword = "encoded-current")
                val refreshToken = mockk<RefreshToken>(relaxed = true)
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    passwordEncoder.matches("current!", "encoded-current")
                } returns true
                every {
                    passwordEncoder.encode("newPassword1!")
                } returns "encoded-new"
                every {
                    refreshTokenRepository.findAllByUserAndRevokedAtIsNull(
                        user = user,
                    )
                } returns listOf(refreshToken)

                userService.changePassword(
                    email = email,
                    currentPassword = "current!",
                    newPassword = "newPassword1!",
                )

                user.password shouldBe "encoded-new"
                verify {
                    refreshToken.revoke()
                }
            }
        }
    }

    Given("로그인한 사용자가 회원 탈퇴를 요청할 때") {
        When("탈퇴를 진행하면") {
            Then("status를 WITHDRAWN으로 바꾸고 식별 정보를 익명화하며 soft delete하고 refresh token을 삭제한다") {
                val user = activeUser()
                val originalEmail = user.email
                val originalNickname = user.nickname
                val refreshTokens = listOf(mockk<RefreshToken>(relaxed = true))
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    refreshTokenRepository.findAllByUser(
                        user = user,
                    )
                } returns refreshTokens
                every {
                    refreshTokenRepository.deleteAll(
                        refreshTokens,
                    )
                } just runs
                every { userRepository.flush() } just runs
                every { userRepository.delete(user) } just runs

                userService.withdraw(
                    email = email,
                )

                user.status shouldBe UserStatus.WITHDRAWN
                user.email shouldNotBe originalEmail
                user.nickname shouldNotBe originalNickname
                verify {
                    refreshTokenRepository.deleteAll(
                        refreshTokens,
                    )
                    userRepository.delete(user)
                }
                verify(exactly = 0) {
                    objectStorage.deleteObject(objectKey = any())
                }
            }
        }

        When("프로필 이미지가 있는 회원이 탈퇴하면") {
            Then("프로필 이미지 S3 객체도 삭제한다") {
                val previousKey = "profile-images/2026/09/0000000000001.png"
                val user = activeUser(currentProfileImageKey = previousKey)
                val refreshTokens = listOf(mockk<RefreshToken>(relaxed = true))
                every {
                    userRepository.findByEmail(
                        email = email,
                    )
                } returns user
                every {
                    refreshTokenRepository.findAllByUser(
                        user = user,
                    )
                } returns refreshTokens
                every {
                    refreshTokenRepository.deleteAll(
                        refreshTokens,
                    )
                } just runs
                every { userRepository.flush() } just runs
                every { userRepository.delete(user) } just runs

                userService.withdraw(
                    email = email,
                )

                verify {
                    objectStorage.deleteObject(objectKey = previousKey)
                }
            }
        }
    }
})
