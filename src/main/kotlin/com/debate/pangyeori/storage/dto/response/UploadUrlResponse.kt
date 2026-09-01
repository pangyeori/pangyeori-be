package com.debate.pangyeori.storage.dto.response

data class UploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
    val expiresInSeconds: Long,
)
