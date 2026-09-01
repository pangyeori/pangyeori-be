package com.debate.pangyeori.storage.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class PresignFailedException : BusinessException(
    errorCode = ErrorCode.EXTERNAL_API_ERROR,
)
