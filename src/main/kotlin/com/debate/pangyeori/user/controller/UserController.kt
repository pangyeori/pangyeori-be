package com.debate.pangyeori.user.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.user.dto.NicknameDuplicateResponse
import com.debate.pangyeori.user.dto.UserCreateRequest
import com.debate.pangyeori.user.service.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @PostMapping
    fun createUser(
        @RequestBody @Valid request: UserCreateRequest,
    ): ResponseEntity<Void> {
        userService.createUser(
            email = request.email!!,
            password = request.password!!,
            nickname = request.nickname!!,
            profileImageUrl = request.profileImageUrl,
        )

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping("/nickname/duplicate")
    fun checkNicknameDuplicate(
        @RequestParam
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
        nickname: String,
    ): ApiResponse<NicknameDuplicateResponse> {
        val response = userService.checkNicknameDuplicate(
            nickname = nickname,
        )

        return ApiResponse.success(
            data = response,
        )
    }
}
