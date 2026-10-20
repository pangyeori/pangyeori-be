package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiError
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.debate.service.DebateEarlyEndService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class DebateEarlyEndStompController(
    private val debateEarlyEndService: DebateEarlyEndService,
) {
    private val logger = KotlinLogging.logger {}

    @MessageMapping("/debates/{debateId}/early-end/propose")
    fun propose(
        @DestinationVariable debateId: String,
        principal: Principal,
    ) {
        debateEarlyEndService.propose(
            debateId = debateId,
            userId = principal.name,
        )
    }

    @MessageMapping("/debates/{debateId}/early-end/accept")
    fun accept(
        @DestinationVariable debateId: String,
        principal: Principal,
    ) {
        debateEarlyEndService.accept(
            debateId = debateId,
            userId = principal.name,
        )
    }

    @MessageMapping("/debates/{debateId}/early-end/decline")
    fun decline(
        @DestinationVariable debateId: String,
        principal: Principal,
    ) {
        debateEarlyEndService.decline(
            debateId = debateId,
            userId = principal.name,
        )
    }

    @MessageMapping("/debates/{debateId}/early-end/cancel")
    fun cancel(
        @DestinationVariable debateId: String,
        principal: Principal,
    ) {
        debateEarlyEndService.cancel(
            debateId = debateId,
            userId = principal.name,
        )
    }

    @MessageExceptionHandler(BusinessException::class)
    @SendToUser("/queue/errors")
    fun handleBusinessException(
        e: BusinessException,
    ): ApiError {
        logger.warn { "조기 종료 처리가 거부되었습니다: ${e.errorCode.name}" }
        return ApiError(
            code = e.errorCode.name,
            message = e.message ?: e.errorCode.message,
        )
    }
}
