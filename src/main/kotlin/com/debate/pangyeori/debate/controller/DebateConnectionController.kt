package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.response.DebatePresenceResponse
import com.debate.pangyeori.debate.service.DebateConnectionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateConnectionController(
    private val debateConnectionService: DebateConnectionService,
) {
    @GetMapping("/{debateId}/presence")
    fun getPresence(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<DebatePresenceResponse>> {
        val response = debateConnectionService.getPresence(
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
