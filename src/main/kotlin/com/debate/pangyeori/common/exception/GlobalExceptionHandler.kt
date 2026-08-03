package com.debate.pangyeori.common.exception

import com.debate.pangyeori.common.dto.ApiError
import com.debate.pangyeori.common.dto.ApiError.FieldError
import com.debate.pangyeori.common.dto.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        e: BusinessException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val error = ApiError(
            code = e.errorCode.name,
            message = e.message ?: e.errorCode.message,
        )
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.fail(error))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        e: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val details = e.bindingResult.fieldErrors.map {
            FieldError(
                field = it.field,
                message = it.defaultMessage ?: "",
            )
        }
        val error = ApiError(
            code = ErrorCode.INVALID_INPUT.name,
            message = ErrorCode.INVALID_INPUT.message,
            details = details,
        )
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(error))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        e: Exception,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val error = ApiError(
            code = ErrorCode.INTERNAL_SERVER_ERROR.name,
            message = ErrorCode.INTERNAL_SERVER_ERROR.message,
        )
        return ResponseEntity
            .internalServerError()
            .body(ApiResponse.fail(error))
    }
}
