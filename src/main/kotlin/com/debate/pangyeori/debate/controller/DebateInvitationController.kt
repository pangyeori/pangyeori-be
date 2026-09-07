package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.response.DebateInvitationResponse
import com.debate.pangyeori.debate.service.DebateParticipationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debate-invitations")
class DebateInvitationController(
    private val debateParticipationService: DebateParticipationService,
) {
    @GetMapping("/{token}")
    fun get(
        principal: Principal,
        @PathVariable token: String,
    ): ResponseEntity<ApiResponse<DebateInvitationResponse>> {
        val response = debateParticipationService.getInvitation(
            token = token,
            userEmail = principal.name,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }
}
