package com.debate.pangyeori.common.dto

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> success(
            data: T,
        ) = ApiResponse(
            success = true,
            data = data,
        )

        fun fail(
            error: ApiError,
        ) = ApiResponse<Nothing>(
            success = false,
            error = error,
        )
    }
}
