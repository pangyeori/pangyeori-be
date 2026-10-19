package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.response.DebateEarlyEndResponse
import com.debate.pangyeori.debate.service.DebateEarlyEndService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateEarlyEndController(
    private val debateEarlyEndService: DebateEarlyEndService,
) {
    @GetMapping("/{debateId}/early-end")
    fun getStatus(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<DebateEarlyEndResponse>> {
        val response = debateEarlyEndService.getStatus(
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
