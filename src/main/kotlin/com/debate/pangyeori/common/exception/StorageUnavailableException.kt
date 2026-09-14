package com.debate.pangyeori.common.exception

class StorageUnavailableException : BusinessException(
    errorCode = ErrorCode.EXTERNAL_API_ERROR,
)
