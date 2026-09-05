package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class InvalidPositionException : BusinessException(
    errorCode = ErrorCode.INVALID_POSITION,
)
