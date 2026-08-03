package com.debate.pangyeori.common.dto

data class ApiError(
    val code: String,
    val message: String,
    val details: List<FieldError>? = null,
) {
    data class FieldError(
        val field: String,
        val message: String,
    )
}
