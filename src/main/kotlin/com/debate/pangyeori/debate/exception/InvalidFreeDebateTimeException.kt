package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class InvalidFreeDebateTimeException : BusinessException(
    errorCode = ErrorCode.INVALID_FREE_DEBATE_TIME,
)
