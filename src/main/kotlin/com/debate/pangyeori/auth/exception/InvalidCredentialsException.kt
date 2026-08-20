package com.debate.pangyeori.auth.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class InvalidCredentialsException : BusinessException(
    errorCode = ErrorCode.INVALID_CREDENTIALS,
)
