package com.debate.pangyeori.common.exception

abstract class BusinessException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
