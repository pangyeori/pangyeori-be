package com.debate.pangyeori.user.service

import com.debate.pangyeori.auth.repository.EmailVerificationRedisRepository
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.dto.response.NicknameDuplicateResponse
import com.debate.pangyeori.user.dto.response.UserResponse
import com.debate.pangyeori.user.exception.EmailAlreadyExistsException
import com.debate.pangyeori.user.exception.EmailNotVerifiedException
import com.debate.pangyeori.user.exception.NicknameAlreadyExistsException
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailVerificationRedisRepository: EmailVerificationRedisRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun createUser(
        email: String,
        password: String,
        nickname: String,
        profileImageUrl: String?,
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
            profileImageUrl = profileImageUrl?.trim()?.takeIf { it.isNotEmpty() },
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
        val user = userRepository.findByEmail(
            email = email,
        ) ?: throw UserNotFoundException()

        return UserResponse.from(
            user = user,
        )
    }
}
