package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiResponse
import com.debate.pangyeori.debate.dto.response.DebateStreamTicketResponse
import com.debate.pangyeori.debate.exception.DebateStreamTicketInvalidException
import com.debate.pangyeori.debate.service.DebateStreamService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.security.Principal

@RestController
@RequestMapping("/api/v1/debates")
class DebateStreamController(
    private val debateStreamService: DebateStreamService,
) {
    @PostMapping("/{debateId}/status/stream-tickets")
    fun issueStreamTicket(
        principal: Principal,
        @PathVariable debateId: String,
    ): ResponseEntity<ApiResponse<DebateStreamTicketResponse>> {
        val response = debateStreamService.issueTicket(
            debateId = debateId,
            userEmail = principal.name,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(
                data = response,
            ),
        )
    }

    @GetMapping("/{debateId}/status/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamStatus(
        @PathVariable debateId: String,
        @RequestParam(required = false) ticket: String?,
    ): SseEmitter = debateStreamService.subscribe(
        debateId = debateId,
        ticket = ticket ?: throw DebateStreamTicketInvalidException(),
    )
}
