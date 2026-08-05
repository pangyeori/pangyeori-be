package com.debate.pangyeori.user.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.UserCreateRequest
import com.debate.pangyeori.user.dto.UserCreateResponse
import com.debate.pangyeori.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @PostMapping
    fun createUser(
        @RequestBody @Valid request: UserCreateRequest,
    ): ResponseEntity<ApiResponse<UserCreateResponse>> {
        val response = userService.createUser(
            email = request.email!!,
            password = request.password!!,
            nickname = request.nickname!!,
            profileImageUrl = request.profileImageUrl,
        )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }
}
