package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.request.DebateHistorySubmitRequest
import com.debate.pangyeori.debate.dto.response.DebateHistoryResponse
import com.debate.pangyeori.debate.service.DebateHistoryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateHistoryController(
    private val debateHistoryService: DebateHistoryService,
) {
    @PostMapping("/{debateId}/histories")
    fun submit(
        principal: Principal,
        @PathVariable debateId: String,
        @RequestBody @Valid request: DebateHistorySubmitRequest,
    ): ResponseEntity<ApiResponse<DebateHistoryResponse>> {
        val response = debateHistoryService.submit(
            debateId = debateId,
            userEmail = principal.name,
            content = request.content!!,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @GetMapping("/{debateId}/histories")
    fun getHistories(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<List<DebateHistoryResponse>>> {
        val response = debateHistoryService.getHistories(
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
