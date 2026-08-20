package com.debate.pangyeori.auth.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class ExpiredTokenException : BusinessException(
    errorCode = ErrorCode.EXPIRED_TOKEN,
)
