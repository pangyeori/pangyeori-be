package com.debate.pangyeori.debate.exception

import com.debate.pangyeori.common.exception.BusinessException
import com.debate.pangyeori.common.exception.ErrorCode

class InvalidTurnTimeException : BusinessException(
    errorCode = ErrorCode.INVALID_TURN_TIME,
)
