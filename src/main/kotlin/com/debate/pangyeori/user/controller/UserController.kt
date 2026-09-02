package com.debate.pangyeori.user.controller

import com.debate.pangyeori.auth.token.RefreshTokenCookieProvider
import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.request.NicknameDuplicateRequest
import com.debate.pangyeori.user.dto.request.PasswordChangeRequest
import com.debate.pangyeori.user.dto.request.UserCreateRequest
import com.debate.pangyeori.user.dto.request.UserProfileUpdateRequest
import com.debate.pangyeori.user.dto.response.NicknameDuplicateResponse
import com.debate.pangyeori.user.dto.response.UserResponse
import com.debate.pangyeori.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val refreshTokenCookieProvider: RefreshTokenCookieProvider,
) {
    @GetMapping("/me")
    fun getMyInfo(
        principal: Principal,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val response = userService.getMyInfo(
            email = principal.name,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @PostMapping
    fun createUser(
        @RequestBody @Valid request: UserCreateRequest,
    ): ResponseEntity<Void> {
        userService.createUser(
            email = request.email!!,
            password = request.password!!,
            nickname = request.nickname!!,
        )

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PatchMapping("/me")
    fun updateProfile(
        principal: Principal,
        @RequestBody @Valid request: UserProfileUpdateRequest,
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val response = userService.updateProfile(
            email = principal.name,
            nickname = request.nickname,
            profileImageKey = request.profileImageKey,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @DeleteMapping("/me/profile-image")
    fun removeProfileImage(
        principal: Principal,
    ): ResponseEntity<Void> {
        userService.removeProfileImage(
            email = principal.name,
        )

        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/me/password")
    fun changePassword(
        principal: Principal,
        @RequestBody @Valid request: PasswordChangeRequest,
    ): ResponseEntity<Void> {
        userService.changePassword(
            email = principal.name,
            currentPassword = request.currentPassword!!,
            newPassword = request.newPassword!!,
        )

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me")
    fun withdraw(
        principal: Principal,
    ): ResponseEntity<Void> {
        userService.withdraw(
            email = principal.name,
        )

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.clear().toString())
            .build()
    }

    @GetMapping("/nickname/duplicate")
    fun checkNicknameDuplicate(
        @Valid request: NicknameDuplicateRequest,
    ): ApiResponse<NicknameDuplicateResponse> {
        val response = userService.checkNicknameDuplicate(
            nickname = request.nickname!!,
        )

        return ApiResponse.success(
            data = response,
        )
    }
}
