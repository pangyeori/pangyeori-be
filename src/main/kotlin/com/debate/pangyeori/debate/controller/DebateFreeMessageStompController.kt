package com.debate.pangyeori.debate.controller

import com.debate.pangyeori.common.dto.ApiError
import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode
import com.debate.pangyeori.debate.dto.request.DebateFreeMessageSubmitRequest
import com.debate.pangyeori.debate.service.DebateFreeMessageService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class DebateFreeMessageStompController(
    private val debateFreeMessageService: DebateFreeMessageService,
) {
    private val logger = KotlinLogging.logger {}

    @MessageMapping("/debates/{debateId}/free-messages")
    fun submit(
        @DestinationVariable debateId: String,
        @Payload @Valid request: DebateFreeMessageSubmitRequest,
        principal: Principal,
    ) {
        debateFreeMessageService.submit(
            debateId = debateId,
            userId = principal.name,
            content = request.content!!,
        )
    }

    @MessageExceptionHandler(BusinessException::class)
    @SendToUser("/queue/errors")
    fun handleBusinessException(
        e: BusinessException,
    ): ApiError {
        logger.warn { "자유 토론 메시지 제출이 거부되었습니다: ${e.errorCode.name}" }
        return ApiError(
            code = e.errorCode.name,
            message = e.message ?: e.errorCode.message,
        )
    }

    @MessageExceptionHandler(MethodArgumentNotValidException::class)
    @SendToUser("/queue/errors")
    fun handleValidationException(
        e: MethodArgumentNotValidException,
    ): ApiError {
        val details = e.bindingResult!!.fieldErrors.map {
            ApiError.FieldError(
                field = it.field,
                message = it.defaultMessage ?: "",
            )
        }
        return ApiError(
            code = ErrorCode.INVALID_INPUT.name,
            message = ErrorCode.INVALID_INPUT.message,
            details = details,
        )
    }
}
