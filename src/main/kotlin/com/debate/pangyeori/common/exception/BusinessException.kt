package com.debate.pangyeori.common.exception

abstract class BusinessException(
    val errorCode: ErrorCode,
    message: String,
) : RuntimeException(message)
