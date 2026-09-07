package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.request.DebateCreateRequest
import com.debate.pangyeori.debate.dto.request.DebateGuestAcceptRequest
import com.debate.pangyeori.debate.dto.response.DebateCreateResponse
import com.debate.pangyeori.debate.dto.response.DebateGuestAcceptResponse
import com.debate.pangyeori.debate.dto.response.DebateParticipationResponse
import com.debate.pangyeori.debate.dto.response.DebateStatusResponse
import com.debate.pangyeori.debate.service.DebateParticipationService
import com.debate.pangyeori.debate.service.DebateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateController(
    private val debateService: DebateService,
    private val debateParticipationService: DebateParticipationService,
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

    @PostMapping("/{debateId}/invitations/request")
    fun requestParticipation(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<DebateParticipationResponse>> {
        val response = debateParticipationService.requestParticipation(
            debateId = debateId,
            userEmail = principal.name,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @DeleteMapping("/{debateId}/invitations/request")
    fun cancelParticipation(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<Void> {
        debateParticipationService.cancelParticipation(
            debateId = debateId,
            userEmail = principal.name,
        )

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{debateId}/guest/accept")
    fun acceptGuest(
        principal: Principal,
        @PathVariable debateId: String,
        @RequestBody @Valid request: DebateGuestAcceptRequest,
    ): ResponseEntity<ApiResponse<DebateGuestAcceptResponse>> {
        val response = debateParticipationService.acceptGuest(
            debateId = debateId,
            hostEmail = principal.name,
            guestUserId = request.userId!!,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @GetMapping("/{debateId}/status")
    fun getStatus(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<DebateStatusResponse>> {
        val response = debateParticipationService.getStatus(
            debateId = debateId,
            userEmail = principal.name,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }
}
