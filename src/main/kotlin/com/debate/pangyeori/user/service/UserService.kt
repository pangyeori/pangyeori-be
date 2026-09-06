package com.debate.pangyeori.user.service

import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.auth.repository.RefreshTokenRepository
import com.debate.pangyeori.storage.client.ObjectStorage
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.dto.response.NicknameDuplicateResponse
import com.debate.pangyeori.user.dto.response.UserResponse
import com.debate.pangyeori.user.exception.*
import com.debate.pangyeori.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.core.exception.SdkException

@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailVerificationRedisRepository: EmailVerificationRedisRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val objectStorage: ObjectStorage,
    private val passwordEncoder: PasswordEncoder,
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun createUser(
        email: String,
        password: String,
        nickname: String,
    ) {
        val normalizedEmail = email.trim().lowercase()
        val normalizedNickname = nickname.trim()

        if (!emailVerificationRedisRepository.isVerified(
                email = normalizedEmail,
            )
        ) {
            throw EmailNotVerifiedException()
        }
        if (userRepository.existsByEmail(
                email = normalizedEmail,
            )
        ) {
            throw EmailAlreadyExistsException()
        }
        if (userRepository.existsByNickname(
                nickname = normalizedNickname,
            )
        ) {
            throw NicknameAlreadyExistsException()
        }

        val user = User.create(
            email = normalizedEmail,
            password = passwordEncoder.encode(password)!!,
            nickname = normalizedNickname,
        )
        userRepository.save(user)
        emailVerificationRedisRepository.clearVerified(
            email = normalizedEmail,
        )
    }

    @Transactional(readOnly = true)
    fun checkNicknameDuplicate(
        nickname: String,
    ) = NicknameDuplicateResponse(
        duplicated = userRepository.existsByNickname(
            nickname = nickname.trim(),
        ),
    )

    @Transactional(readOnly = true)
    fun getMyInfo(
        email: String,
    ): UserResponse {
        val user = findUser(
            email = email,
        )

        return UserResponse.from(
            user = user,
        )
    }

    @Transactional
    fun updateProfile(
        email: String,
        nickname: String?,
        profileImageKey: String?,
    ): UserResponse {
        val user = findUser(
            email = email,
        )

        if (nickname != null) {
            val normalizedNickname = nickname.trim()
            if (normalizedNickname != user.nickname &&
                userRepository.existsByNickname(
                    nickname = normalizedNickname,
                )
            ) {
                throw NicknameAlreadyExistsException()
            }
            user.changeNickname(
                newNickname = normalizedNickname,
            )
        }
        if (profileImageKey != null) {
            val previousKey = user.profileImageKey
            user.changeProfileImage(
                newKey = profileImageKey,
            )
            if (previousKey != null && previousKey != profileImageKey) {
                deleteObjectQuietly(
                    objectKey = previousKey,
                )
            }
        }

        return UserResponse.from(
            user = user,
        )
    }

    @Transactional
    fun removeProfileImage(
        email: String,
    ) {
        val user = findUser(
            email = email,
        )

        val previousKey = user.profileImageKey
        user.removeProfileImage()
        if (previousKey != null) {
            deleteObjectQuietly(
                objectKey = previousKey,
            )
        }
    }

    @Transactional
    fun changePassword(
        email: String,
        currentPassword: String,
        newPassword: String,
    ) {
        val user = findUser(
            email = email,
        )

        if (!passwordEncoder.matches(currentPassword, user.password)) {
            throw InvalidCurrentPasswordException()
        }

        user.changePassword(
            newPassword = passwordEncoder.encode(newPassword)!!,
        )
        revokeRefreshTokens(
            user = user,
        )
    }

    @Transactional
    fun withdraw(
        email: String,
    ) {
        val user = findUser(
            email = email,
        )
        val previousKey = user.profileImageKey

        deleteRefreshTokens(
            user = user,
        )
        user.withdraw()
        userRepository.flush()
        userRepository.delete(user)

        if (previousKey != null) {
            deleteObjectQuietly(
                objectKey = previousKey,
            )
        }
    }

    private fun findUser(
        email: String,
    ): User = userRepository.findByEmail(
        email = email,
    ) ?: throw UserNotFoundException()

    private fun revokeRefreshTokens(
        user: User,
    ) {
        refreshTokenRepository.findAllByUserAndRevokedAtIsNull(
            user = user,
        ).forEach { it.revoke() }
    }

    private fun deleteRefreshTokens(
        user: User,
    ) {
        refreshTokenRepository.deleteAll(
            refreshTokenRepository.findAllByUser(
                user = user,
            ),
        )
    }

    private fun deleteObjectQuietly(
        objectKey: String,
    ) {
        try {
            objectStorage.deleteObject(
                objectKey = objectKey,
            )
        } catch (e: SdkException) {
            logger.warn(e) { "프로필 이미지 삭제에 실패했습니다. objectKey=$objectKey" }
        }
    }
}
