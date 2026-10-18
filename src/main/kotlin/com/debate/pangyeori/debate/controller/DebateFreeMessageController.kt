package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.common.dto.CursorPage
import com.debate.pangyeori.debate.dto.request.DebateFreeMessageListRequest
import com.debate.pangyeori.debate.dto.response.DebateFreeMessageResponse
import com.debate.pangyeori.debate.service.DebateFreeMessageService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateFreeMessageController(
    private val debateFreeMessageService: DebateFreeMessageService,
) {
    @GetMapping("/{debateId}/free-messages")
    fun getMessages(
        principal: Principal,
        @PathVariable debateId: String,
        @Valid request: DebateFreeMessageListRequest,
    ): ResponseEntity<ApiResponse<CursorPage<DebateFreeMessageResponse>>> {
        val response = debateFreeMessageService.getMessages(
            debateId = debateId,
            userEmail = principal.name,
            cursor = request.cursor,
            pageSize = request.pageSize,
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                data = response,
            ),
        )
    }
}
