package com.debate.pangyeori.common.dto

data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
)
