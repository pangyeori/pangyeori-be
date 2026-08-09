package com.debate.pangyeori.user.controller

import com.debate.pangyeori.user.dto.SignInRequest
import com.debate.pangyeori.user.dto.SignInResponse
import com.debate.pangyeori.user.dto.RefreshTokenRequest
import com.debate.pangyeori.user.dto.CurrentUserResponse
import com.debate.pangyeori.user.service.UserAuthService
import com.debate.pangyeori.common.dto.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping

@RestController
@RequestMapping("/api/v1/users")
class UserAuthController(
    private val userAuthService: UserAuthService,
) {
    @PostMapping("/signin")
    fun signIn(@RequestBody @Valid request: SignInRequest): ApiResponse<SignInResponse> =
        ApiResponse.success(
            userAuthService.signIn(
                email = request.email!!,
                password = request.password!!,
            ),
        )

    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshTokenRequest): ApiResponse<SignInResponse> =
        ApiResponse.success(userAuthService.refresh(request.refreshToken!!))

    @PostMapping("/logout")
    fun logout(@RequestBody @Valid request: RefreshTokenRequest): ResponseEntity<Void> {
        userAuthService.logout(request.refreshToken!!)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): ApiResponse<CurrentUserResponse> =
        ApiResponse.success(CurrentUserResponse(email = authentication.name))
}
