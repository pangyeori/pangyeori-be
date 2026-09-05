package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.request.DebateCreateRequest
import com.debate.pangyeori.debate.dto.response.DebateCreateResponse
import com.debate.pangyeori.debate.service.DebateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateController(
    private val debateService: DebateService,
) {
    @PostMapping
    fun create(
        principal: Principal,
        @RequestBody @Valid request: DebateCreateRequest,
    ): ResponseEntity<ApiResponse<DebateCreateResponse>> {
        val response = debateService.create(
            hostEmail = principal.name,
            title = request.title!!,
            description = request.description,
            hostPosition = request.hostPosition,
            turnTimeSeconds = request.turnTimeSeconds!!,
            freeDebateTimeSeconds = request.freeDebateTimeSeconds!!,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                data = response,
            ),
        )
    }

}
